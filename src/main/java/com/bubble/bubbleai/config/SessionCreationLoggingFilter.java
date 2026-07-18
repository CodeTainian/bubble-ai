package com.bubble.bubbleai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 临时记录创建 Session 的具体 HTTP 请求。
 *
 * <p>该过滤器必须排在 Spring Session 过滤器之后，才能观察由 Spring Session
 * 包装过的请求。日志只记录 Session ID 的不可逆指纹，不泄露完整登录凭证。</p>
 */
@Component
@Order(SessionRepositoryFilter.DEFAULT_ORDER + 1)
@ConditionalOnProperty(prefix = "bubble-ai.session", name = "creation-log-enabled", havingValue = "true")
@Slf4j
public class SessionCreationLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_LOG_VALUE_LENGTH = 160;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession sessionBeforeRequest = request.getSession(false);
        String sessionIdBeforeRequest = sessionBeforeRequest == null ? null : sessionBeforeRequest.getId();
        boolean requestedSessionIdPresent = request.getRequestedSessionId() != null;
        boolean requestedSessionIdValid = requestedSessionIdPresent && request.isRequestedSessionIdValid();
        SessionCreationTrackingRequest trackingRequest = new SessionCreationTrackingRequest(request);

        try {
            filterChain.doFilter(trackingRequest, response);
        } finally {
            HttpSession sessionAfterRequest = request.getSession(false);
            if (sessionAfterRequest != null
                    && !sessionAfterRequest.getId().equals(sessionIdBeforeRequest)) {
                log.warn(
                        "SESSION_CREATED method={} uri={} status={} dispatcher={} sessionFingerprint={} "
                                + "requestedSessionIdPresent={} requestedSessionIdValid={} remote={} userAgent={}",
                        request.getMethod(),
                        safeLogValue(request.getRequestURI()),
                        response.getStatus(),
                        request.getDispatcherType(),
                        fingerprint(sessionAfterRequest.getId()),
                        requestedSessionIdPresent,
                        requestedSessionIdValid,
                        resolveRemoteAddress(request),
                        safeLogValue(request.getHeader("User-Agent")));
            }
        }
    }

    private String resolveRemoteAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return safeLogValue(forwardedFor.split(",", 2)[0].trim());
        }
        return safeLogValue(request.getRemoteAddr());
    }

    private String safeLogValue(String value) {
        if (value == null) {
            return "-";
        }
        String sanitized = value.replace('\r', '_').replace('\n', '_');
        return sanitized.length() <= MAX_LOG_VALUE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LOG_VALUE_LENGTH);
    }

    private String fingerprint(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private final class SessionCreationTrackingRequest extends HttpServletRequestWrapper {

        private boolean creationCallLogged;

        private SessionCreationTrackingRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public HttpSession getSession(boolean create) {
            HttpSession sessionBeforeCall = super.getSession(false);
            HttpSession session = super.getSession(create);
            if (create && sessionBeforeCall == null && session != null && !creationCallLogged) {
                creationCallLogged = true;
                HttpServletRequest request = (HttpServletRequest) getRequest();
                log.warn(
                        "SESSION_CREATION_CALL method={} uri={} remote={} userAgent={}",
                        request.getMethod(),
                        safeLogValue(request.getRequestURI()),
                        resolveRemoteAddress(request),
                        safeLogValue(request.getHeader("User-Agent")),
                        new SessionCreationCallTrace());
            }
            return session;
        }

        @Override
        public HttpSession getSession() {
            return getSession(true);
        }
    }

    private static final class SessionCreationCallTrace extends RuntimeException {

        private SessionCreationCallTrace() {
            super("Temporary stack trace for the Session creation call");
        }
    }
}
