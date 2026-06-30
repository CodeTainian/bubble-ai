package com.bubble.bubbleai.ai;



import com.bubble.bubbleai.ai.guardrail.PromptSafetyInputGuardrail;
import com.bubble.bubbleai.ai.guardrail.RetryOutputGuardrail;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.ai.tools.*;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.service.ChatHistoryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;
    @Autowired
    @Qualifier("reasoningStreamingChatModelPrototype")
    private ObjectProvider<StreamingChatModel> reasoningStreamingChatModelProvider;
    @Autowired
    @Qualifier("streamingChatModelPrototype")
    private ObjectProvider<StreamingChatModel> openAiStreamingChatModelProvider;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private ToolManager toolManager;
    @Resource
    private PromptSafetyInputGuardrail promptSafetyInputGuardrail;
    @Resource
    private RetryOutputGuardrail retryOutputGuardrail;

    /**
     * Ai服务实例缓存
     */
    private final Cache<String,AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {},原因: {}",key,cause);
            }).build();


    public AiCodeGeneratorService getAiCodeGeneratorService(long appId){
        return getAiCodeGeneratorService(appId,CodeGenTypeEnum.HTML);
    }


    public AiCodeGeneratorService getAiCodeGeneratorService(long appId,CodeGenTypeEnum codeGenTypeEnum){
        String cacheKey = buildCacheKey(appId, codeGenTypeEnum);
        return serviceCache.get(cacheKey,
                key -> createAiCodeGeneratorService(appId,codeGenTypeEnum));
    }

    /**
     * 根据appId构建独立的对话记忆
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId,CodeGenTypeEnum codeGenTypeEnum){
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        //从数据库中加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId,chatMemory,20);
        //根据不同的代码生成类型选择不同的模型配置
        return switch (codeGenTypeEnum){
            //React 项目生成使用的推理模型,使用多例模式的 StreamingChatModel 解决并发问题
            case REACT_PROJECT -> {
              StreamingChatModel reasoningStreamingChatModel = reasoningStreamingChatModelProvider.getObject();
              yield  AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools((Object[]) toolManager.getAllTools())
                    .inputGuardrails(promptSafetyInputGuardrail)
//                      .outputGuardrails(retryOutputGuardrail)
                    .hallucinatedToolNameStrategy(toolExecutionRequest ->
                            ToolExecutionResultMessage.from(toolExecutionRequest,"Error:there is no tool called"+
                                    toolExecutionRequest.name()))
                    .build();
            }
            //HTML和多文件生成时使用
            case HTML,MULTI_FIlE ->{
                StreamingChatModel openAiStreamingChatModelProviderObject = openAiStreamingChatModelProvider.getObject();
             yield  AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModelProviderObject)
                    .chatMemory(chatMemory)
                     .inputGuardrails(promptSafetyInputGuardrail)
//                     .outputGuardrails(retryOutputGuardrail)
                    .build();
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码类型"+codeGenTypeEnum.getValue());
        };
    }

    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService(){
        return getAiCodeGeneratorService(0);
    }

    /**
     *
     * @param appId;
     * @param codeGenTypeEnum;
     * @return 缓存key
     */
    private String buildCacheKey(long appId,CodeGenTypeEnum codeGenTypeEnum){
        return appId+ "_" +codeGenTypeEnum.getValue();
    }



}
