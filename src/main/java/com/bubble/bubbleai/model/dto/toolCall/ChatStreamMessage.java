package com.bubble.bubbleai.model.dto.toolCall;


import com.bubble.bubbleai.ai.model.enums.ChatStreamMessageStatusEnum;
import com.bubble.bubbleai.ai.model.enums.ChatStreamMessageTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一流式消息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatStreamMessage {
    /**
     * 当前事件 ID
     */
    private String id;

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 消息类型
     */
    private ChatStreamMessageTypeEnum type;

    /**
     * 消息状态
     */
    private ChatStreamMessageStatusEnum status;

    /**
     * 文本内容
     */
    private String content;

    /**
     * 仅服务端内部使用的完整内容，例如文件代码。避免大代码块进入 SSE 响应影响前端实时展示。
     */
    @JsonIgnore
    private String internalContent;

    /**
     * 工具调用信息
     */
    private ToolCallInfo tool;

    /**
     * 扩展信息
     */
    private Map<String, Object> metadata;

    /**
     * 时间戳
     */
    private Long timestamp;

}
