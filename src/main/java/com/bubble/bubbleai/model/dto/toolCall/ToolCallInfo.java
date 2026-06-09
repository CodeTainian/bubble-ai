package com.bubble.bubbleai.model.dto.toolCall;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *  工具调用信息DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallInfo {

    /**
     * 工具调用 ID
     */
    private String id;

    /**
     * 工具名称，例如 writeFile、readFile
     */
    private String name;

    /**
     * 工具入参，通常是 JSON 字符串
     */
    private String arguments;

    /**
     * 工具执行结果
     */
    private String result;

    /**
     * 是否执行成功
     */
    private Boolean success;

    /**
     * 耗时，单位毫秒
     */
    private Long elapsedMillis;

}
