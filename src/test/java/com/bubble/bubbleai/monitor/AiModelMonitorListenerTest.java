package com.bubble.bubbleai.monitor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class AiModelMonitorListenerTest {

    private AiModelMetricsCollector collector;
    private AiModelMonitorListener listener;

    @BeforeEach
    void setUp() {
        collector = mock(AiModelMetricsCollector.class);
        listener = new AiModelMonitorListener();
        ReflectionTestUtils.setField(listener, "aiModelMetricsCollector", collector);
    }

    @AfterEach
    void cleanUp() {
        MonitorContextHolder.clearContext();
    }

    @Test
    void missingContextNeverBreaksRequestOrErrorCallbacks() {
        ChatRequest request = request();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        assertDoesNotThrow(() -> listener.onRequest(new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes)));
        assertDoesNotThrow(() -> listener.onError(new ChatModelErrorContext(
                new IllegalStateException("failed"), request, ModelProvider.OPEN_AI, attributes)));

        verify(collector).recordRequest("system", "unknown", "test-model", "started");
        verify(collector).recordRequest("system", "unknown", "test-model", "error");
        verify(collector).recordError("system", "unknown", "test-model", "IllegalStateException");
    }

    @Test
    void responseUsesRequestAttributeAfterThreadLocalWasCleared() {
        ChatRequest request = request();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        MonitorContext context = MonitorContext.builder().userId("u-1").appId("a-1").build();
        try (MonitorContextHolder.Scope ignored = MonitorContextHolder.openScope(context)) {
            listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attributes));
        }
        assertNull(MonitorContextHolder.getContext());

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .modelName("test-model")
                .build();
        assertDoesNotThrow(() -> listener.onResponse(new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes)));

        verify(collector).recordRequest("u-1", "a-1", "test-model", "success");
    }

    private ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .modelName("test-model")
                .build();
    }
}
