package com.bubble.bubbleai.model.dto.user;

import com.bubble.bubbleai.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends PageRequest implements Serializable {

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
     * 用户简介
     */
    private String userProfile;
    /**
     * 用户角色
     */
    private String userRole;

    private static final long serialVersionUID = 1L;



}
