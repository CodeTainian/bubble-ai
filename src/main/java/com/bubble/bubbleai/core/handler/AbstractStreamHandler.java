package com.bubble.bubbleai.core.handler;

import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.core.builder.BuildResult;
import com.bubble.bubbleai.core.builder.ReactProjectBuildRepairService;
import com.bubble.bubbleai.core.builder.ReactProjectBuilder;
import com.bubble.bubbleai.exception.SseErrorMessageUtils;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.model.enums.ChatMessageSource;
import com.bubble.bubbleai.model.enums.GenerationState;
import com.bubble.bubbleai.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.io.File;

/** Stream handling template whose lifecycle includes React build and repair. */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractStreamHandler implements StreamHandler {

    protected final ChatHistoryService chatHistoryService;
    private final AppCoverGenerator appCoverGenerator;

    @Resource
    private ReactProjectBuilder reactProjectBuilder;
    @Resource
    private ReactProjectBuildRepairService reactProjectBuildRepairService;
    @Resource
    private GenerationStateService generationStateService;

    @Override
    public Flux<String> handle(StreamHandleContext context) {
        StringBuilder displayMessageBuilder = new StringBuilder();
        StringBuilder modelMessageBuilder = new StringBuilder();
        Flux<String> transformed = transform(context.originFlux(), modelMessageBuilder);
        if (CodeGenTypeEnum.REACT_PROJECT.equals(context.codeGenType())) {
            return transformed
                    .concatWith(Flux.defer(() -> buildReactProjectAndGenerateCover(context)))
                    .doOnNext(displayMessageBuilder::append)
                    .doOnComplete(() -> saveAiMessage(context, displayMessageBuilder, modelMessageBuilder))
                    .doOnError(error -> {
                        saveAiMessage(context, displayMessageBuilder, modelMessageBuilder);
                        saveErrorMessage(context, error);
                    });
        }
        return transformed
                .doOnNext(displayMessageBuilder::append)
                .doOnComplete(() -> {
                    saveAiMessage(context, displayMessageBuilder, modelMessageBuilder);
                    generationStateService.transition(context.appId(), context.generationId(),
                            GenerationState.SUCCESS, 0);
                    appCoverGenerator.generateAsync(context.appId(), context.codeGenType());
                })
                .doOnError(error -> {
                    saveAiMessage(context, displayMessageBuilder, modelMessageBuilder);
                    saveErrorMessage(context, error);
                });
    }

    protected abstract Flux<String> transform(Flux<String> originFlux, StringBuilder aiMessageBuilder);

    private void saveAiMessage(StreamHandleContext context, StringBuilder displayBuilder,
                               StringBuilder modelBuilder) {
        String modelMessage = modelBuilder.toString();
        String displayMessage = StrUtil.blankToDefault(displayBuilder.toString(), modelMessage);
        if (StrUtil.isBlank(displayMessage) && StrUtil.isBlank(modelMessage)) {
            return;
        }
        try {
            chatHistoryService.addChatMessage(
                    context.appId(), context.loginUser().getId(),
                    ChatHistoryMessageTypeEnum.AI.getValue(),
                    displayMessage, StrUtil.blankToDefault(modelMessage, displayMessage),
                    ChatMessageSource.AI_OUTPUT,
                    true, null, null);
        } catch (Exception e) {
            log.error("Unable to save AI chat history, appId={}", context.appId(), e);
        }
    }

    private Flux<String> buildReactProjectAndGenerateCover(StreamHandleContext context) {
        return Flux.create(sink -> {
            generationStateService.transition(context.appId(), context.generationId(),
                    GenerationState.BUILDING, 0);
            sink.next("\n\n正在检查项目构建结果\n\n");
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator
                    + CodeGenTypeEnum.REACT_PROJECT.getValue() + "_" + context.appId();
            reactProjectBuilder.buildProjectWithResultAsync(projectPath)
                    .thenCompose(buildResult -> {
                        if (buildResult.success()) {
                            generationStateService.transition(context.appId(), context.generationId(),
                                    GenerationState.SUCCESS, 0);
                            sink.next("\n\n项目构建成功\n\n");
                            return java.util.concurrent.CompletableFuture.completedFuture(buildResult);
                        }
                        generationStateService.transition(context.appId(), context.generationId(),
                                GenerationState.BUILD_FAILED, 0);
                        return reactProjectBuildRepairService.repairAndBuildAsync(
                                context.appId(), context.generationId(), context.originalUserMessage(),
                                context.loginUser(), context.monitorContext(), buildResult, sink::next);
                    })
                    .whenComplete((finalResult, error) -> {
                        if (error != null) {
                            log.error("React build/repair pipeline failed, appId={}, generationId={}",
                                    context.appId(), context.generationId(), error);
                            generationStateService.transition(context.appId(), context.generationId(),
                                    GenerationState.FAILED_MAX_ATTEMPTS, 0);
                            sink.next("\n\n自动修复未成功，请稍后重试\n\n");
                            sink.complete();
                            return;
                        }
                        if (finalResult != null && finalResult.success()) {
                            appCoverGenerator.generateAsync(context.appId(), context.codeGenType());
                        }
                        sink.complete();
                    });
        });
    }

    private void saveErrorMessage(StreamHandleContext context, Throwable error) {
        String message = SseErrorMessageUtils.resolveMessage(error);
        if (StrUtil.isBlank(message)) {
            message = "AI 生成流程异常";
        }
        generationStateService.transition(context.appId(), context.generationId(),
                GenerationState.FAILED_MAX_ATTEMPTS, 0);
        try {
            chatHistoryService.addInternalMessage(
                    context.appId(), context.loginUser().getId(),
                    ChatHistoryMessageTypeEnum.ERROR.getValue(),
                    "generationId=" + context.generationId() + "\nmessage=" + message,
                    ChatMessageSource.SYSTEM);
        } catch (Exception e) {
            log.error("Unable to save internal generation error, appId={}", context.appId(), e);
        }
    }
}
