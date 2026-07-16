package com.bubble.bubbleaiapp.core.handler;

import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleaiapp.core.builder.BuildResult;
import com.bubble.bubbleaiapp.core.builder.ReactProjectBuildRepairService;
import com.bubble.bubbleaiapp.core.builder.ReactProjectBuilder;
import com.bubble.bubbleai.exception.SseErrorMessageUtils;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.model.enums.ChatMessageSource;
import com.bubble.bubbleai.model.enums.GenerationState;
import com.bubble.bubbleaiapp.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 流式响应处理通用模板。
 */
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

    /**
     * 将原始流转换为前端可读的文本流，同时按需收集完整 AI 消息。
     *
     * @param originFlux       原始 AI 响应流
     * @param aiMessageBuilder 完整 AI 消息收集器
     * @return 转换后的响应流
     */
    protected abstract Flux<String> transform(Flux<String> originFlux, StringBuilder aiMessageBuilder);

    private void saveAiMessage(StreamHandleContext context, StringBuilder displayBuilder,
                               StringBuilder modelBuilder) {
        String modelMessage = modelBuilder.toString();
        String displayMessage = StrUtil.blankToDefault(displayBuilder.toString(), modelMessage);
        if (StrUtil.isNotBlank(displayMessage) || StrUtil.isNotBlank(modelMessage)) {
            try {
                chatHistoryService.addChatMessage(context.appId(), context.loginUser().getId(),
                        ChatHistoryMessageTypeEnum.AI.getValue(), displayMessage,
                        StrUtil.blankToDefault(modelMessage, displayMessage),
                        ChatMessageSource.AI_OUTPUT, true, null, null);
            } catch (Exception e) {
                log.error("save AI chat history failed, appId={}", context.appId(), e);
            }
        }
    }

    private Flux<String> buildReactProjectAndGenerateCover(StreamHandleContext context) {
        return Flux.create(sink -> {
            generationStateService.transition(context.appId(), context.generationId(), GenerationState.BUILDING, 0);
            sink.next("\n\n正在检查项目构建结果\n\n");
            String projectDirName = CodeGenTypeEnum.REACT_PROJECT.getValue() + "_" + context.appId();
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + projectDirName;
            reactProjectBuilder.buildProjectWithResultAsync(projectPath)
                .thenCompose(buildResult -> {
                    if (buildResult.success()) {
                        generationStateService.transition(context.appId(), context.generationId(), GenerationState.SUCCESS, 0);
                        sink.next("\n\n项目构建成功\n\n");
                        return java.util.concurrent.CompletableFuture.completedFuture(buildResult);
                    }
                    generationStateService.transition(context.appId(), context.generationId(), GenerationState.BUILD_FAILED, 0);
                    return reactProjectBuildRepairService.repairAndBuildAsync(
                            context.appId(), context.generationId(), context.originalUserMessage(),
                            context.loginUser(), context.monitorContext(), buildResult, sink::next);
                })
                .whenComplete((finalBuildResult, error) -> {
                    if (error != null) {
                        log.error("React build/repair pipeline failed, appId={}, generationId={}",
                                context.appId(), context.generationId(), error);
                        generationStateService.transition(context.appId(), context.generationId(),
                                GenerationState.FAILED_MAX_ATTEMPTS, 0);
                        sink.next("\n\n自动修复未成功，请稍后重试\n\n");
                        sink.complete();
                        return;
                    }
                    if (finalBuildResult != null && finalBuildResult.success()) {
                        appCoverGenerator.generateAsync(context.appId(), context.codeGenType());
                    }
                    sink.complete();
                });
        });
    }

    private void saveErrorMessage(StreamHandleContext context, Throwable error) {
        String errorMessage = SseErrorMessageUtils.resolveMessage(error);
        if (StrUtil.isBlank(errorMessage)) {
            errorMessage = error.getClass().getSimpleName();
        }
        try {
            chatHistoryService.addInternalMessage(context.appId(), context.loginUser().getId(),
                    ChatHistoryMessageTypeEnum.ERROR.getValue(),
                    "generationId=" + context.generationId() + "\nAI 回复失败：" + errorMessage,
                    ChatMessageSource.SYSTEM);
        } catch (Exception e) {
            log.error("save AI error chat history failed, appId={}", context.appId(), e);
        }
    }
}
