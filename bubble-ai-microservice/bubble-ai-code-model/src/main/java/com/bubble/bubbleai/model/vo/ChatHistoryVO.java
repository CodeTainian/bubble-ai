package com.bubble.bubbleai.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史视图
 */
@Data
public class ChatHistoryVO implements Serializable {

    /**
     * 对话历史 ID
     */
    private Long id;

    /**
     * 消息
     */
    private String message;

    private String messageSource;

    private Boolean visibleToUser;

    /**
     * 消息类型：user/ai/error
     */
    private String messageType;

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 父消息 id
     */
    private Long parentId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 应用信息
     */
    private AppVO app;

    /**
     * 用户信息
     */
    private UserVO user;

    @Serial
    private static final long serialVersionUID = 1L;
}
