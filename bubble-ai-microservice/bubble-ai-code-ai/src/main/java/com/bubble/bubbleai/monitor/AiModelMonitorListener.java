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
    private static final String START = "request_start_time";
    private static final String CONTEXT = "monitor_context";
    @Resource private AiModelMetricsCollector collector;

    @Override
    public void onRequest(ChatModelRequestContext event) {
        try {
            event.attributes().put(START, Instant.now());
            MonitorContext current = MonitorContextHolder.getContext();
            MonitorContext context = current == null ? MonitorContext.system() : current.snapshot();
            event.attributes().put(CONTEXT, context);
            safe(() -> collector.recordRequest(value(context.getUserId(), "system"),
                    value(context.getAppId(), "unknown"), value(event.chatRequest().modelName(), "unknown"), "started"));
        } catch (Exception e) { log.debug("AI request monitoring skipped", e); }
    }

    @Override
    public void onResponse(ChatModelResponseContext event) {
        try {
            MonitorContext context = context(event.attributes());
            String user = value(context.getUserId(), "system");
            String app = value(context.getAppId(), "unknown");
            String model = value(event.chatResponse().modelName(), "unknown");
            safe(() -> collector.recordRequest(user, app, model, "success"));
            responseTime(event.attributes(), user, app, model);
            TokenUsage usage = event.chatResponse().metadata().tokenUsage();
            if (usage != null) {
                safe(() -> collector.recordTokenUsage(user, app, model, "input", usage.inputTokenCount()));
                safe(() -> collector.recordTokenUsage(user, app, model, "output", usage.outputTokenCount()));
                safe(() -> collector.recordTokenUsage(user, app, model, "total", usage.totalTokenCount()));
            }
        } catch (Exception e) { log.debug("AI response monitoring skipped", e); }
    }

    @Override
    public void onError(ChatModelErrorContext event) {
        try {
            MonitorContext context = context(event.attributes());
            String user = value(context.getUserId(), "system");
            String app = value(context.getAppId(), "unknown");
            String model = value(event.chatRequest().modelName(), "unknown");
            String type = event.error() == null ? "unknown" : event.error().getClass().getSimpleName();
            safe(() -> collector.recordRequest(user, app, model, "error"));
            safe(() -> collector.recordError(user, app, model, type));
            responseTime(event.attributes(), user, app, model);
        } catch (Exception e) { log.debug("AI error monitoring skipped", e); }
    }

    private MonitorContext context(Map<Object, Object> attributes) {
        Object stored = attributes.get(CONTEXT);
        if (stored instanceof MonitorContext context) return context;
        MonitorContext current = MonitorContextHolder.getContext();
        return current == null ? MonitorContext.system() : current.snapshot();
    }
    private void responseTime(Map<Object, Object> attributes, String user, String app, String model) {
        Object value = attributes.get(START);
        if (value instanceof Instant start) safe(() -> collector.recordResponseTime(user, app, model, Duration.between(start, Instant.now())));
    }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private void safe(Runnable action) { try { action.run(); } catch (Exception e) { log.debug("AI metric recording failed", e); } }
}
