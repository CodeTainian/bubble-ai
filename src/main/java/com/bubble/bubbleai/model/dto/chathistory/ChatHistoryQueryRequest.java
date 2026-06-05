package com.bubble.bubbleai.model.dto.chathistory;

import com.bubble.bubbleai.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    /**
     * 对话历史 ID
     */
    private Long id;

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型：user/ai/error
     */
    private String messageType;

    /**
     * 父消息 id
     */
    private Long parentId;

    /**
     * 游标：当前已加载最早一条消息的 id
     */
    private Long lastId;

    /**
     * 游标：当前已加载最早一条消息的创建时间
     */
    private LocalDateTime lastCreateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
