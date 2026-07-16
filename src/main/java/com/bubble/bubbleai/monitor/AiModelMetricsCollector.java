package com.bubble.bubbleai.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class AiModelMetricsCollector {

    @Resource
    private MeterRegistry meterRegistry;

    // 缓存已创建的指标，避免重复创建（按指标类型分离缓存）
    private final ConcurrentMap<String, Counter> requestCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> tokenCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> responseTimersCache = new ConcurrentHashMap<>();

    /**
     * 记录请求次数
     */
    public void recordRequest(String userId, String appId, String modelName, String status) {
        String safeUserId = safeTag(userId, "system");
        String safeAppId = safeTag(appId, "unknown");
        String safeModelName = safeTag(modelName, "unknown");
        String safeStatus = safeTag(status, "unknown");
        String key = String.format("%s_%s_%s_%s", safeUserId, safeAppId, safeModelName, safeStatus);
        Counter counter = requestCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_requests_total")
                        .description("AI模型总请求次数")
                        .tag("user_id", safeUserId)
                        .tag("app_id", safeAppId)
                        .tag("model_name", safeModelName)
                        .tag("status", safeStatus)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * 记录错误
     */
    public void recordError(String userId, String appId, String modelName, String errorMessage) {
        String safeUserId = safeTag(userId, "system");
        String safeAppId = safeTag(appId, "unknown");
        String safeModelName = safeTag(modelName, "unknown");
        String safeErrorType = safeTag(errorMessage, "unknown");
        String key = String.format("%s_%s_%s_%s", safeUserId, safeAppId, safeModelName, safeErrorType);
        Counter counter = errorCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_errors_total")
                        .description("AI模型错误次数")
                        .tag("user_id", safeUserId)
                        .tag("app_id", safeAppId)
                        .tag("model_name", safeModelName)
                        .tag("error_type", safeErrorType)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * 记录Token消耗
     */
    public void recordTokenUsage(String userId, String appId, String modelName,
                                 String tokenType, long tokenCount) {
        String safeUserId = safeTag(userId, "system");
        String safeAppId = safeTag(appId, "unknown");
        String safeModelName = safeTag(modelName, "unknown");
        String safeTokenType = safeTag(tokenType, "unknown");
        String key = String.format("%s_%s_%s_%s", safeUserId, safeAppId, safeModelName, safeTokenType);
        Counter counter = tokenCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_tokens_total")
                        .description("AI模型Token消耗总数")
                        .tag("user_id", safeUserId)
                        .tag("app_id", safeAppId)
                        .tag("model_name", safeModelName)
                        .tag("token_type", safeTokenType)
                        .register(meterRegistry)
        );
        counter.increment(tokenCount);
    }

    /**
     * 记录响应时间
     */
    public void recordResponseTime(String userId, String appId, String modelName, Duration duration) {
        String safeUserId = safeTag(userId, "system");
        String safeAppId = safeTag(appId, "unknown");
        String safeModelName = safeTag(modelName, "unknown");
        String key = String.format("%s_%s_%s", safeUserId, safeAppId, safeModelName);
        Timer timer = responseTimersCache.computeIfAbsent(key, k ->
                Timer.builder("ai_model_response_duration_seconds")
                        .description("AI模型响应时间")
                        .tag("user_id", safeUserId)
                        .tag("app_id", safeAppId)
                        .tag("model_name", safeModelName)
                        .register(meterRegistry)
        );
        timer.record(duration);
    }

    private String safeTag(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.length() <= 100 ? value : value.substring(0, 100);
    }
}
