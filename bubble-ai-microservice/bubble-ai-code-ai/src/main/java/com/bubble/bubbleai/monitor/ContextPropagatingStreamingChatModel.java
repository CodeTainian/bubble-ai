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

public final class ContextPropagatingStreamingChatModel implements StreamingChatModel {
    private final StreamingChatModel delegate;
    public ContextPropagatingStreamingChatModel(StreamingChatModel delegate) { this.delegate = delegate; }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        MonitorContext current = MonitorContextHolder.getContext();
        MonitorContext snapshot = current == null ? MonitorContext.system() : current.snapshot();
        delegate.doChat(request, new StreamingChatResponseHandler() {
            public void onPartialResponse(String value) { run(() -> handler.onPartialResponse(value)); }
            public void onPartialToolExecutionRequest(int i, ToolExecutionRequest r) { run(() -> handler.onPartialToolExecutionRequest(i, r)); }
            public void onCompleteToolExecutionRequest(int i, ToolExecutionRequest r) { run(() -> handler.onCompleteToolExecutionRequest(i, r)); }
            public void onCompleteResponse(ChatResponse response) { run(() -> handler.onCompleteResponse(response)); }
            public void onError(Throwable error) { run(() -> handler.onError(error)); }
            private void run(Runnable callback) { MonitorContextHolder.runWithContext(snapshot, callback); }
        });
    }
    public ChatRequestParameters defaultRequestParameters() { return delegate.defaultRequestParameters(); }
    public List<ChatModelListener> listeners() { return delegate.listeners(); }
    public ModelProvider provider() { return delegate.provider(); }
    public Set<Capability> supportedCapabilities() { return delegate.supportedCapabilities(); }
}
