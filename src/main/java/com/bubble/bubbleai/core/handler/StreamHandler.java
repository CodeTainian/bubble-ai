package com.bubble.bubbleai.core.handler;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import reactor.core.publisher.Flux;

/**
 * 流式响应处理策略。
 */
public interface StreamHandler {

    /**
     * 当前处理器是否支持指定代码生成类型。
     *
     * @param codeGenType 代码生成类型
     * @return 是否支持
     */
    boolean supports(CodeGenTypeEnum codeGenType);

    /**
     * 处理 AI 原始流。
     *
     * @param context 流处理上下文
     * @return 处理后的前端响应流
     */
    Flux<String> handle(StreamHandleContext context);
}
