package com.bubble.bubbleaiapp.core.builder;

import com.bubble.bubbleai.ai.AiCodeGeneratorService;
import com.bubble.bubbleaiapp.ai.AiCodeGeneratorServiceFactory;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashSet;
import java.util.Set;

/** Executes one isolated AI repair attempt. */
@Component
@RequiredArgsConstructor
public class ReactProjectRepairAgent {

    private final AiCodeGeneratorServiceFactory serviceFactory;
    private final ReactGenerationProperties properties;

    public RepairOutcome repair(long appId, String generationId, String prompt) throws Exception {
        AiCodeGeneratorService service = serviceFactory.createRepairAiCodeGeneratorService(appId, generationId);
        TokenStream stream = service.repairReactProjectCodeStream(appId, prompt);
        CompletableFuture<RepairOutcome> completion = new CompletableFuture<>();
        StringBuilder summary = new StringBuilder();
        Set<String> tools = new LinkedHashSet<>();
        stream.onPartialResponse(partial -> appendBounded(summary, partial, 2000))
                .onPartialToolExecutionRequest((index, request) -> { })
                .onCompleteToolExecutionRequest((index, request) -> { })
                .onToolExecuted(execution -> tools.add(execution.request().name()))
                .onCompleteResponse(response -> completion.complete(
                        new RepairOutcome(summary.toString().trim(), Set.copyOf(tools))))
                .onError(completion::completeExceptionally)
                .start();
        return completion.get(Math.max(1, properties.getRepairAiTimeoutSeconds()), TimeUnit.SECONDS);
    }

    private void appendBounded(StringBuilder target, String value, int maxLength) {
        if (value == null || target.length() >= maxLength) return;
        target.append(value, 0, Math.min(value.length(), maxLength - target.length()));
    }

    public record RepairOutcome(String summary, Set<String> executedTools) {
        public String safeSummary() {
            String toolSummary = executedTools == null || executedTools.isEmpty()
                    ? "未报告工具调用" : "已调用工具：" + String.join(", ", executedTools);
            return (summary == null || summary.isBlank() ? "AI 已完成本次检查" : summary)
                    + "；" + toolSummary;
        }
    }
}
