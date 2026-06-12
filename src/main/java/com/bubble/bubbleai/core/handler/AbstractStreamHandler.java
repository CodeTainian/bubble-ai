package com.bubble.bubbleai.core.handler;

import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 流式响应处理通用模板。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractStreamHandler implements StreamHandler {

    protected final ChatHistoryService chatHistoryService;

    private final AppCoverGenerator appCoverGenerator;

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
        appCoverGenerator.generateAsync(context.appId(), context.codeGenType());
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
