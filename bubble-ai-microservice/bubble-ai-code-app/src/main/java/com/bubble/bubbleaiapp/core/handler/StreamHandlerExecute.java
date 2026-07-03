package com.bubble.bubbleaiapp.core.handler;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StreamHandlerExecute {

    private final List<StreamHandler> streamHandlers;

    public Flux<String> doExecute(Flux<String> originFlux, long appId, User loginUser, CodeGenTypeEnum codeGenType) {
        StreamHandler streamHandler = streamHandlers.stream()
                .filter(handler -> handler.supports(codeGenType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SYSTEM_ERROR,
                        "不支持的流式处理类型：" + (codeGenType == null ? "null" : codeGenType.getValue())
                ));
        StreamHandleContext context = new StreamHandleContext(originFlux, appId, loginUser, codeGenType);
        return streamHandler.handle(context);

    }

}
