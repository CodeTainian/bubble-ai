package com.bubble.bubbleai.monitor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContextPropagatingStreamingChatModelTest {

    @AfterEach
    void cleanUp() {
        MonitorContextHolder.clearContext();
    }

    @Test
    void concurrentCallbacksKeepUserContextsSeparateAndCleanWorkerThreads() throws Exception {
        Map<String, String> callbackUsers = new ConcurrentHashMap<>();
        Map<String, Boolean> workerContextsCleared = new ConcurrentHashMap<>();
        CountDownLatch completed = new CountDownLatch(2);
        StreamingChatModel delegate = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                String requestName = ((UserMessage) request.messages().getFirst()).singleText();
                Thread.ofVirtual().start(() -> {
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("ok"))
                            .build());
                    workerContextsCleared.put(requestName, MonitorContextHolder.getContext() == null);
                    completed.countDown();
                });
            }
        };
        StreamingChatModel model = new ContextPropagatingStreamingChatModel(delegate);

        invoke(model, "request-1", "user-1", callbackUsers);
        invoke(model, "request-2", "user-2", callbackUsers);

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals("user-1", callbackUsers.get("request-1"));
        assertEquals("user-2", callbackUsers.get("request-2"));
        assertTrue(workerContextsCleared.values().stream().allMatch(Boolean::booleanValue));
        assertNull(MonitorContextHolder.getContext());
    }

    @Test
    void errorCallbackRestoresContextAndCleansWorkerThread() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> callbackUser = new AtomicReference<>();
        AtomicBoolean workerCleaned = new AtomicBoolean();
        StreamingChatModel delegate = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                Thread.ofVirtual().start(() -> {
                    handler.onError(new IllegalStateException("boom"));
                    workerCleaned.set(MonitorContextHolder.getContext() == null);
                    completed.countDown();
                });
            }
        };
        StreamingChatModel model = new ContextPropagatingStreamingChatModel(delegate);
        MonitorContext context = MonitorContext.builder().userId("error-user").appId("app").build();
        try (MonitorContextHolder.Scope ignored = MonitorContextHolder.openScope(context)) {
            model.doChat(ChatRequest.builder().messages(UserMessage.from("request")).build(),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            fail("unexpected completion");
                        }

                        @Override
                        public void onError(Throwable error) {
                            callbackUser.set(MonitorContextHolder.getContext().getUserId());
                        }
                    });
        }

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals("error-user", callbackUser.get());
        assertTrue(workerCleaned.get());
        assertNull(MonitorContextHolder.getContext());
    }

    private void invoke(StreamingChatModel model, String requestName, String userId,
                        Map<String, String> callbackUsers) {
        ChatRequest request = ChatRequest.builder().messages(UserMessage.from(requestName)).build();
        MonitorContext context = MonitorContext.builder().userId(userId).appId("app").build();
        try (MonitorContextHolder.Scope ignored = MonitorContextHolder.openScope(context)) {
            model.doChat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    callbackUsers.put(requestName, MonitorContextHolder.getContext().getUserId());
                }

                @Override
                public void onError(Throwable error) {
                    fail(error);
                }
            });
        }
    }
}
