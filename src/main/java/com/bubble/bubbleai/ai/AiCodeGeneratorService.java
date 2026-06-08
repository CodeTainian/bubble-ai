package com.bubble.bubbleai.ai;


import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import reactor.core.publisher.Flux;

/**
When using ChatMemory in this way it's also important to evict the memory of a no longer needed conversations in order to avoid memory leaks.
To make the chat memories internally used by an AI service accessible
it's enough that the interface defining it extends the ChatMemoryAccess one
 */
public interface AiCodeGeneratorService extends ChatMemoryAccess {

    /**
     *  生成Html代码
     * @param userMessage 用户消息
     * @return 生成代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     *  生成多文件代码
     * @param userMessage 用户消息
     * @return 生产的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 Html 代码(流式)
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码(流式)
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 生成React项目代码(流式)
     * @param appId;
     * @param userMessage;
     * @return 生成过程的流失响应
     */
    @SystemMessage(fromResource = "prompt/codegen-react-project-system-prompt.txt")
    Flux<String> generateReactProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

}

