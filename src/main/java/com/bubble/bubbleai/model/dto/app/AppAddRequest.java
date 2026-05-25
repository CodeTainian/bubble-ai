package com.bubble.bubbleai.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用添加请求
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    @Serial
    private static final long serialVersionUID = 1L;
}

