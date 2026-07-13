package com.bubble.bubbleai.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 对话生成代码请求。
 */
@Data
public class ChatGenerateRequest implements Serializable {

    /**
     * 应用 ID。
     */
    private Long appId;

    /**
     * 用户消息。
     */
    private String message;

    @Serial
    private static final long serialVersionUID = 1L;
}
