package com.bubble.bubbleai.core.handler;


import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.service.ChatHistoryService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 */
@Component
public class SimpleTextStreamHandler extends AbstractStreamHandler {

    public SimpleTextStreamHandler(ChatHistoryService chatHistoryService, AppCoverGenerator appCoverGenerator) {
        super(chatHistoryService, appCoverGenerator);
    }

    @Override
    public boolean supports(CodeGenTypeEnum codeGenType) {
        return CodeGenTypeEnum.HTML.equals(codeGenType)
                || CodeGenTypeEnum.MULTI_FIlE.equals(codeGenType);
    }

    @Override
    protected Flux<String> transform(Flux<String> originFlux, StringBuilder aiMessageBuilder) {
        return originFlux.doOnNext(aiMessageBuilder::append);
    }
}
