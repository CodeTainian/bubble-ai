package com.bubble.bubbleai.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用更新请求
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * 应用ID
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 优先级
     */
    private Integer priority;

    @Serial
    private static final long serialVersionUID = 1L;
}

