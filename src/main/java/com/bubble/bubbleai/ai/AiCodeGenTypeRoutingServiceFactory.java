package com.bubble.bubbleai.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ai 代码生成类型路由服务工厂
 */
@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    @Autowired
    @Qualifier("routingChatModelPrototype")
    private ObjectProvider<ChatModel> routingChatModelProvider;


    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService(){
        ChatModel routingChatModelProviderObject = routingChatModelProvider.getObject();
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(routingChatModelProviderObject)
                .build();
    }

    /**
     * 默认提供一个bean
     *
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService(){
        return createAiCodeGenTypeRoutingService();
    }


}
