package com.bubble.bubbleai.service;

import com.bubble.bubbleai.model.dto.user.UserQueryRequest;
import com.bubble.bubbleai.model.vo.LoginUserVO;
import com.bubble.bubbleai.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.bubble.bubbleai.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author  <a href="https://github.com/liyupi">Coder-Ashely</a>
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param userAccount 用户账户
     * @param userPassword 用户密码
     * @param checkPassword 确认密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 加密
     * @param userPassword 密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * re-hash userAccount to generate an Encrypted Account for user Avatar；
     * @param userAccount account；
     * @return Encrypted UserAccount
     */
    String getDefaultAvatar(String userAccount);

    /**
     * 获取脱敏的已登录用户信息
     * @return 脱敏后的用户
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     *
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param request 请求
     * @return 脱敏后的用户
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     * @param request session
     * @return 当前用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     * @param request session
     * @return 结果
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏后的单个用户
     * @param user 加密用户
     * @return 脱敏后的用户
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏后的用户列表
     * @param userList 用户列表
     * @return 脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 分页查询用户
     * @param userQueryRequest 请求对象
     * @return 封装好的查询回来的数据
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

}
