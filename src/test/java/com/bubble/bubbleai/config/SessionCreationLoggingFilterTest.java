package com.bubble.bubbleai.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class SessionCreationLoggingFilterTest {

    private final SessionCreationLoggingFilter filter = new SessionCreationLoggingFilter();

    @Test
    void logsRequestThatCreatesSessionWithoutLeakingSessionId(CapturedOutput output)
            throws ServletException, IOException {
        MockHttpServletRequest request = request("POST", "/api/user/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> ((HttpServletRequest) servletRequest).getSession(true));

        assertTrue(request.getSession(false).isNew());
        assertTrue(output.getOut().contains("SESSION_CREATED"));
        assertTrue(output.getOut().contains("SESSION_CREATION_CALL"));
        assertTrue(output.getOut().contains("Temporary stack trace for the Session creation call"));
        assertTrue(output.getOut().contains("method=POST"));
        assertTrue(output.getOut().contains("uri=/api/user/login"));
        assertTrue(output.getOut().contains("sessionFingerprint="));
        assertFalse(output.getOut().contains("sessionId="));
    }

    @Test
    void doesNotCreateOrLogSessionForSessionNeutralRequests(CapturedOutput output)
            throws ServletException, IOException {
        String[][] requests = {
                {"GET", "/api/actuator/prometheus"},
                {"GET", "/api/actuator/health"},
                {"OPTIONS", "/api/app/list/page/vo"},
                {"GET", "/api/user/get/login"}
        };

        for (String[] requestData : requests) {
            MockHttpServletRequest request = request(requestData[0], requestData[1]);
            filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
                // 模拟不访问 HttpSession 的监控、预检和未登录查询请求。
            });
            assertNull(request.getSession(false));
        }

        assertFalse(output.getOut().contains("SESSION_CREATED"));
    }

    @Test
    void doesNotCreateSessionWhenRequestFails(CapturedOutput output) {
        MockHttpServletRequest request = request("GET", "/api/missing");

        assertThrows(ServletException.class, () -> filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> {
                    throw new ServletException("expected test failure");
                }));

        assertNull(request.getSession(false));
        assertFalse(output.getOut().contains("SESSION_CREATED"));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("User-Agent", "session-diagnostic-test");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
