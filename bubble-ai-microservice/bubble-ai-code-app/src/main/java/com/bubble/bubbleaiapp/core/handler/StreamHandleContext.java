package com.bubble.bubbleaiapp.core.handler;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.monitor.MonitorContext;
import reactor.core.publisher.Flux;

/**
 * 流式响应处理上下文。
 *
 * @param originFlux  原始 AI 响应流
 * @param appId       应用 ID
 * @param loginUser   当前登录用户
 * @param codeGenType 代码生成类型
 */
public record StreamHandleContext(
        Flux<String> originFlux,
        long appId,
        User loginUser,
        CodeGenTypeEnum codeGenType,
        String generationId,
        String originalUserMessage,
        MonitorContext monitorContext
) {
}
