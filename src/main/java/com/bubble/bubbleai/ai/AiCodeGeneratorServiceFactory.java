package com.bubble.bubbleai.ai;



import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.ai.model.tools.FileWriteTool;
import com.bubble.bubbleai.constant.AppConstant;
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
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;
    @Resource
    private StreamingChatModel openAiStreamingChatModel;
    @Resource
    private StreamingChatModel reasoningStreamingChatModel;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * Ai服务实例缓存
     */
    private final Cache<String,AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId: {},原因: {}",key,cause);
            }).build();


    public AiCodeGeneratorService getAiCodeGeneratorService(long appId){
        return getAiCodeGeneratorService(appId,CodeGenTypeEnum.HTML);
    }


    public AiCodeGeneratorService getAiCodeGeneratorService(long appId,CodeGenTypeEnum codeGenTypeEnum){
        String cacheKey = buildCacheKey(appId, codeGenTypeEnum);
        return serviceCache.get(cacheKey,
                key -> createAiCodeGeneratorService(appId,codeGenTypeEnum));
    }

    //根据appId构建独立的对话记忆
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
            //React 项目生成使用的推理模型
//            case REACT_PROJECT -> AiServices.builder(AiCodeGeneratorService.class)
//                    .streamingChatModel(reasoningStreamingChatModel)
//                    .chatMemoryProvider(memoryId -> chatMemory)
//                    .tools(new FileWriteTool())
//                    .hallucinatedToolNameStrategy(toolExecutionRequest ->
//                            ToolExecutionResultMessage.from(toolExecutionRequest,"Error:there is no tool called"+
//                                    toolExecutionRequest.name()))
//                    .build();
            case REACT_PROJECT -> {
                Path projectRoot = Path.of(
                        AppConstant.CODE_OUTPUT_ROOT_DIR,
                        AppConstant.buildCodeOutputDirName(CodeGenTypeEnum.REACT_PROJECT, appId)
                );
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(new FileWriteTool(projectRoot))
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,"Error:there is no tool called"+
                                 toolExecutionRequest.name()))
                        .build();
            }


            //HTML和多文件生成时使用
            case HTML,MULTI_FIlE -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码类型"+codeGenTypeEnum.getValue());
        };
    }

    private String buildCacheKey(long appId,CodeGenTypeEnum codeGenTypeEnum){
        return appId+ "_" +codeGenTypeEnum.getValue();
    }



}
