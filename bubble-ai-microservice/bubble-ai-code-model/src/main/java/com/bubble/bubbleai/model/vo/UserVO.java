package com.bubble.bubbleai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 脱敏后的用户
 */
@Data
public class UserVO implements Serializable {

    /**
     * 用户 ID
     */
    private Long id;
    /**
     * 用户昵称
     */
    private String userName;
    /**
     * 用户账户
     */
    private String userAccount;
    /**
     * 用户头像
     */
    private String userAvatar;
    /**
     * 用户简介
     */
    private String userProfile;
    /**
     * 用户角色
     */
    private String userRole;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
