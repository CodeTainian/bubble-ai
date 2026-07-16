package com.bubble.bubbleai.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
@Slf4j
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的键
    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    // 用于监控上下文传递（因为请求和响应事件的触发不是同一个线程）
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";
    
    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        try {
            requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
            MonitorContext current = MonitorContextHolder.getContext();
            MonitorContext context = current == null ? MonitorContext.system() : current.snapshot();
            requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);
            String modelName = safeModelName(requestContext.chatRequest().modelName());
            safeRecord(() -> aiModelMetricsCollector.recordRequest(
                    metricValue(context.getUserId(), "system"),
                    metricValue(context.getAppId(), "unknown"),
                    modelName,
                    "started"));
        } catch (Exception e) {
            log.debug("AI request monitoring was skipped", e);
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        try {
            Map<Object, Object> attributes = responseContext.attributes();
            MonitorContext context = contextFrom(attributes);
            String userId = metricValue(context.getUserId(), "system");
            String appId = metricValue(context.getAppId(), "unknown");
            String modelName = safeModelName(responseContext.chatResponse().modelName());
            safeRecord(() -> aiModelMetricsCollector.recordRequest(userId, appId, modelName, "success"));
            recordResponseTime(attributes, userId, appId, modelName);
            recordTokenUsage(responseContext, userId, appId, modelName);
        } catch (Exception e) {
            log.debug("AI response monitoring was skipped", e);
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        try {
            Map<Object, Object> attributes = errorContext.attributes();
            MonitorContext context = contextFrom(attributes);
            String userId = metricValue(context.getUserId(), "system");
            String appId = metricValue(context.getAppId(), "unknown");
            String modelName = safeModelName(errorContext.chatRequest().modelName());
            String errorType = errorContext.error() == null
                    ? "unknown"
                    : errorContext.error().getClass().getSimpleName();
            safeRecord(() -> aiModelMetricsCollector.recordRequest(userId, appId, modelName, "error"));
            safeRecord(() -> aiModelMetricsCollector.recordError(userId, appId, modelName, errorType));
            recordResponseTime(attributes, userId, appId, modelName);
        } catch (Exception e) {
            log.debug("AI error monitoring was skipped", e);
        }
    }


    /**
     * 记录响应时间
     */
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        Instant startTime = (Instant) attributes.get(REQUEST_START_TIME_KEY);
        if (startTime == null) {
            return;
        }
        Duration responseTime = Duration.between(startTime, Instant.now());
        safeRecord(() -> aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, responseTime));
    }

    /**
     * 记录Token使用情况
     */
    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage != null) {
            safeRecord(() -> aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "input", tokenUsage.inputTokenCount()));
            safeRecord(() -> aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "output", tokenUsage.outputTokenCount()));
            safeRecord(() -> aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "total", tokenUsage.totalTokenCount()));
        }
    }

    private MonitorContext contextFrom(Map<Object, Object> attributes) {
        Object stored = attributes.get(MONITOR_CONTEXT_KEY);
        if (stored instanceof MonitorContext context) {
            return context;
        }
        MonitorContext current = MonitorContextHolder.getContext();
        return current == null ? MonitorContext.system() : current.snapshot();
    }

    private String metricValue(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String safeModelName(String modelName) {
        return metricValue(modelName, "unknown");
    }

    private void safeRecord(Runnable recorder) {
        try {
            recorder.run();
        } catch (Exception e) {
            log.debug("AI metric recording failed", e);
        }
    }
}
