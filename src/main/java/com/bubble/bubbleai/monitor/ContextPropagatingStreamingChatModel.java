package com.bubble.bubbleai.monitor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;
import java.util.Set;

/**
 * Captures request context before the HTTP client changes threads and restores
 * it only while invoking each callback. The scope always restores/removes the
 * prior value, so pooled threads cannot retain another user's identity.
 */
public final class ContextPropagatingStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;

    public ContextPropagatingStreamingChatModel(StreamingChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        MonitorContext current = MonitorContextHolder.getContext();
        MonitorContext snapshot = current == null ? MonitorContext.system() : current.snapshot();
        delegate.doChat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                run(snapshot, () -> handler.onPartialResponse(partialResponse));
            }

            @Override
            public void onPartialToolExecutionRequest(int index, ToolExecutionRequest request) {
                run(snapshot, () -> handler.onPartialToolExecutionRequest(index, request));
            }

            @Override
            public void onCompleteToolExecutionRequest(int index, ToolExecutionRequest request) {
                run(snapshot, () -> handler.onCompleteToolExecutionRequest(index, request));
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                run(snapshot, () -> handler.onCompleteResponse(completeResponse));
            }

            @Override
            public void onError(Throwable error) {
                run(snapshot, () -> handler.onError(error));
            }
        });
    }

    private void run(MonitorContext context, Runnable callback) {
        MonitorContextHolder.runWithContext(context, callback);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
