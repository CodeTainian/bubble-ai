package com.bubble.bubbleai.core.handler;

import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.core.builder.ReactProjectBuilder;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.service.ChatHistoryService;
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

    @Override
    public Flux<String> handle(StreamHandleContext context) {
        StringBuilder aiMessageBuilder = new StringBuilder();
        return transform(context.originFlux(), aiMessageBuilder)
                .doOnComplete(() -> saveAiMessageAndGenerateCover(context, aiMessageBuilder))
                .doOnError(error -> saveErrorMessage(context, error));
    }

    /**
     * 将原始流转换为前端可读的文本流，同时按需收集完整 AI 消息。
     *
     * @param originFlux       原始 AI 响应流
     * @param aiMessageBuilder 完整 AI 消息收集器
     * @return 转换后的响应流
     */
    protected abstract Flux<String> transform(Flux<String> originFlux, StringBuilder aiMessageBuilder);

    private void saveAiMessageAndGenerateCover(StreamHandleContext context, StringBuilder aiMessageBuilder) {
        String aiMessage = aiMessageBuilder.toString();
        if (StrUtil.isNotBlank(aiMessage)) {
            try {
                chatHistoryService.addChatMessage(
                        context.appId(),
                        context.loginUser().getId(),
                        ChatHistoryMessageTypeEnum.AI.getValue(),
                        aiMessage,
                        null
                );
            } catch (Exception e) {
                log.error("save AI chat history failed, appId={}", context.appId(), e);
            }
        }
        if (CodeGenTypeEnum.REACT_PROJECT.equals(context.codeGenType())) {
            buildReactProjectAndGenerateCover(context);
            return;
        }
        appCoverGenerator.generateAsync(context.appId(), context.codeGenType());
    }

    private void buildReactProjectAndGenerateCover(StreamHandleContext context) {
        String projectDirName = CodeGenTypeEnum.REACT_PROJECT.getValue() + "_" + context.appId();
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + projectDirName;
        reactProjectBuilder.buildProjectAsync(projectPath)
                .thenAccept(buildSuccess -> {
                    if (Boolean.TRUE.equals(buildSuccess)) {
                        appCoverGenerator.generateAsync(context.appId(), context.codeGenType());
                    } else {
                        log.warn("skip generating app cover because React project build failed, appId={}", context.appId());
                    }
                })
                .exceptionally(error -> {
                    log.error("build React project before generating app cover failed, appId={}", context.appId(), error);
                    return null;
                });
    }

    private void saveErrorMessage(StreamHandleContext context, Throwable error) {
        String errorMessage = error.getMessage();
        if (StrUtil.isBlank(errorMessage)) {
            errorMessage = error.getClass().getSimpleName();
        }
        try {
            chatHistoryService.addChatMessage(
                    context.appId(),
                    context.loginUser().getId(),
                    ChatHistoryMessageTypeEnum.ERROR.getValue(),
                    "AI 回复失败：" + errorMessage,
                    null
            );
        } catch (Exception e) {
            log.error("save AI error chat history failed, appId={}", context.appId(), e);
        }
    }
}
