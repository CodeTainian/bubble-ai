package com.bubble.bubbleai.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserUpdateRequest implements Serializable {

    /**
     * 用户ID
     */
    private Long Id;
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

    private static final long serialVersionUID = 1L;

}
