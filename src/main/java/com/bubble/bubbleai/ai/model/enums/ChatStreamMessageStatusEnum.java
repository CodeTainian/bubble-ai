package com.bubble.bubbleai.ai.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;



@Getter
@AllArgsConstructor
public enum ChatStreamMessageStatusEnum {
    RUNNING("running", "进行中"),

    SUCCESS("success", "成功"),

    ERROR("error", "失败"),

    DONE("done", "完成");

    @JsonValue
    private final String value;

    private final String text;
}
