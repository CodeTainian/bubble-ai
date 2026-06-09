package com.bubble.bubbleai.core.SSE;


import com.bubble.bubbleai.ai.model.enums.ChatStreamMessageStatusEnum;
import com.bubble.bubbleai.ai.model.enums.ChatStreamMessageTypeEnum;
import com.bubble.bubbleai.model.dto.toolCall.ChatStreamMessage;
import com.bubble.bubbleai.model.dto.toolCall.ToolCallInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ChatStreamMessageFactory {

    private ChatStreamMessageFactory(){}

    public static ChatStreamMessage aiResponse(Long appId ,String content){
        return base(appId, ChatStreamMessageTypeEnum.AI_RESPONSE, ChatStreamMessageStatusEnum.RUNNING)
                .content(content)
                .build();
    }

    public static ChatStreamMessage thinking(Long appId, String content) {
        return base(appId, ChatStreamMessageTypeEnum.THINKING, ChatStreamMessageStatusEnum.RUNNING)
                .content(content)
                .build();
    }

    public static ChatStreamMessage step(Long appId, Integer step,String title,String path){
        return base(appId,ChatStreamMessageTypeEnum.STEP,ChatStreamMessageStatusEnum.RUNNING)
                .content(title)
                .metadata(Map.of("step",step,"title",title,"path",path==null?"":path))
                .build();
    }

    public static ChatStreamMessage fileWrite(Long appId, Integer step, String fileName, String path, String language, String description, String content) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("step", step);
        metadata.put("fileName", fileName);
        metadata.put("path", path);
        metadata.put("language", language);
        metadata.put("description", description);

        return base(appId, ChatStreamMessageTypeEnum.FILE_WRITE, ChatStreamMessageStatusEnum.RUNNING)
                .content(content)
                .metadata(metadata)
                .build();
    }

    public static ChatStreamMessage toolCallStart(Long appId, Integer step, String toolName, String path, String description) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("step", step);
        metadata.put("path", path);
        metadata.put("description", description);

        return base(appId, ChatStreamMessageTypeEnum.TOOL_CALL_START, ChatStreamMessageStatusEnum.RUNNING)
                .content("正在调用工具：" + toolName)
                .tool(ToolCallInfo.builder()
                        .name(toolName)
                        .success(null)
                        .build())
                .metadata(metadata)
                .build();
    }

    public static ChatStreamMessage toolCallResult(Long appId, String toolName, String result) {
        return base(appId, ChatStreamMessageTypeEnum.TOOL_CALL_RESULT, ChatStreamMessageStatusEnum.SUCCESS)
                .content("工具调用完成：" + toolName)
                .tool(ToolCallInfo.builder()
                        .name(toolName)
                        .result(result)
                        .success(true)
                        .build())
                .build();
    }

    public static ChatStreamMessage toolCallStart(Long appId, String toolId, String toolName, String arguments) {
        return base(appId, ChatStreamMessageTypeEnum.TOOL_CALL_START, ChatStreamMessageStatusEnum.RUNNING)
                .content("正在调用工具：" + toolName)
                .tool(ToolCallInfo.builder()
                        .id(toolId)
                        .name(toolName)
                        .arguments(arguments)
                        .success(null)
                        .build())
                .build();
    }

    public static ChatStreamMessage toolCallResult(Long appId, String toolId, String toolName, String arguments, String result, boolean success, Long elapsedMillis) {
        return base(
                appId,
                ChatStreamMessageTypeEnum.TOOL_CALL_RESULT,
                success ? ChatStreamMessageStatusEnum.SUCCESS : ChatStreamMessageStatusEnum.ERROR
        )
                .content(success ? "工具调用完成：" + toolName : "工具调用失败：" + toolName)
                .tool(ToolCallInfo.builder()
                        .id(toolId)
                        .name(toolName)
                        .arguments(arguments)
                        .result(result)
                        .success(success)
                        .elapsedMillis(elapsedMillis)
                        .build())
                .build();
    }

    public static ChatStreamMessage error(Long appId, Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = "AI 生成过程发生未知错误";
        }

        return base(appId, ChatStreamMessageTypeEnum.ERROR, ChatStreamMessageStatusEnum.ERROR)
                .content(message)
                .metadata(Map.of(
                        "errorClass", throwable.getClass().getSimpleName()
                ))
                .build();
    }

    public static ChatStreamMessage done(Long appId) {
        return base(appId, ChatStreamMessageTypeEnum.DONE, ChatStreamMessageStatusEnum.DONE)
                .content("生成完成")
                .build();
    }

    private static ChatStreamMessage.ChatStreamMessageBuilder base(Long appId, ChatStreamMessageTypeEnum type, ChatStreamMessageStatusEnum status) {
        return ChatStreamMessage.builder()
                .id(UUID.randomUUID().toString())
                .appId(appId)
                .type(type)
                .status(status)
                .timestamp(System.currentTimeMillis());
    }

}
