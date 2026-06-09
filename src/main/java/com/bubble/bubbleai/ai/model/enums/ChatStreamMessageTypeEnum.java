package com.bubble.bubbleai.ai.model.enums;


import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型枚举
 */

@Getter
@AllArgsConstructor
public enum ChatStreamMessageTypeEnum {

    AI_RESPONSE("ai_response","AI 正式响应"),
    THINKING("thinking","AI 深度思考"),
    STEP("step", "生成步骤"),
    TOOL_CALL_START("tool_call_start","工具调用开始"),
    TOOL_CALL_RESULT("tool_call_result","工具调用结束"),
    FILE_WRITE("file_write", "文件写入"),
    ERROR("error","错误消息"),
    DONE("done","流失响应结束");

    @JsonValue
    private final String value;
    private final String text;

}
