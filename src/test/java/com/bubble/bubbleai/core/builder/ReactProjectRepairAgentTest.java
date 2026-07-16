package com.bubble.bubbleai.core.builder;

import com.bubble.bubbleai.ai.AiCodeGeneratorService;
import com.bubble.bubbleai.ai.AiCodeGeneratorServiceFactory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReactProjectRepairAgentTest {

    @Test
    @SuppressWarnings("unchecked")
    void registersOptionalToolStreamingCallbacksBeforeStarting() throws Exception {
        AiCodeGeneratorServiceFactory factory = mock(AiCodeGeneratorServiceFactory.class);
        AiCodeGeneratorService service = mock(AiCodeGeneratorService.class);
        TokenStream stream = mock(TokenStream.class);
        ReactGenerationProperties properties = new ReactGenerationProperties();
        properties.setRepairAiTimeoutSeconds(5);

        when(factory.createRepairAiCodeGeneratorService(1L, "g-1")).thenReturn(service);
        when(service.repairReactProjectCodeStream(1L, "repair")).thenReturn(stream);
        when(stream.onPartialResponse(any())).thenReturn(stream);
        when(stream.onPartialToolExecutionRequest(any())).thenReturn(stream);
        when(stream.onCompleteToolExecutionRequest(any())).thenReturn(stream);
        when(stream.onToolExecuted(any())).thenReturn(stream);
        when(stream.onCompleteResponse(any())).thenReturn(stream);
        when(stream.onError(any())).thenReturn(stream);
        doAnswer(invocation -> {
            Consumer<ChatResponse> completion = (Consumer<ChatResponse>) mockingDetails(stream)
                    .getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("onCompleteResponse"))
                    .findFirst().orElseThrow().getArgument(0);
            completion.accept(mock(ChatResponse.class));
            return null;
        }).when(stream).start();

        ReactProjectRepairAgent.RepairOutcome outcome =
                new ReactProjectRepairAgent(factory, properties).repair(1L, "g-1", "repair");

        assertNotNull(outcome);
        verify(stream).onPartialToolExecutionRequest(any());
        verify(stream).onCompleteToolExecutionRequest(any());
        verify(stream).start();
    }
}
