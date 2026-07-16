package com.bubble.bubbleai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.constant.UserConstant;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.model.dto.user.UserQueryRequest;
import com.bubble.bubbleai.model.enums.UserRoleEnum;
import com.bubble.bubbleai.model.vo.LoginUserVO;
import com.bubble.bubbleai.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.mapper.UserMapper;
import com.bubble.bubbleai.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.bubble.bubbleai.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author  <a href="https://github.com/CodeTainian"></a>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

    private static final String ENGLISH_LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final String LETTERS_AND_DIGITS = ENGLISH_LETTERS + "0123456789";

    private static final int USER_NAME_PREFIX_MIN_LENGTH = 6;

    private static final int USER_NAME_PREFIX_MAX_LENGTH = 10;

    private static final int USER_NAME_SUFFIX_LENGTH = 5;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1、校验
        if(StrUtil.hasBlank(userAccount,userPassword,checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if (userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号过短");
        }
        if (userPassword.length()<8 || checkPassword.length()<8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户密码过短");
        }
        if (!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次输入不一致");
        }
        //检查是否重复
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if(count > 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户已存在");
        }
        //密码加密存储
        String encryptPassword = getEncryptPassword(userPassword);
        //插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserRole(UserRoleEnum.USER.getValue());
        user.setUserName(generateDefaultUserName());
        user.setUserAvatar(getDefaultAvatar(userAccount));
        boolean saveResult = this.save(user);
        if(!saveResult){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"注册失败，数据库错误");
        }
        return user.getId();
    }


    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (null == user) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1、校验
        if(StrUtil.hasBlank(userAccount,userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if (userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号错误");
        }
        if (userPassword.length()<8 ) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码错误");
        }
        //2、加密
        String encryptPassword = getEncryptPassword(userPassword);
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if(user == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在或密码错误");
        }
        //3、记录用户的登录状态
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_LOGIN_STATE, user);
        //4、获取脱敏后的用户信息
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        User currentUser = getLoginUserPermitNull(request);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object userObj = session.getAttribute(USER_LOGIN_STATE);
        if (!(userObj instanceof User currentUser) || currentUser.getId() == null) {
            return null;
        }
        Long userId = currentUser.getId();
        currentUser= this.getById(userId);
        return currentUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"未登录");
        }
        Object attribute = session.getAttribute(USER_LOGIN_STATE);
        if (!(attribute instanceof User currentUser) || currentUser.getId() == null) {
            session.invalidate();
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"未登录");
        }
        session.invalidate();
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if(CollUtil.isEmpty(userList)){
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sort = userQueryRequest.getSort();
        String order = userQueryRequest.getOrder();
        QueryWrapper qw = QueryWrapper.create();
                qw.eq("id", id).eq("userRole",userRole).
                like("userProfile", userProfile).
                like("userAccount", userAccount).
                like("userName", userName).eq("isDelete", 0);
        // 排序：白名单 + 空值兜底，ascend/descend 方向映射
        java.util.Set<String> allowSort = java.util.Set.of("id","userName","createTime","updateTime","editTime");
        if (StringUtils.isNotBlank(sort) && allowSort.contains(sort)) {
            boolean asc = "ascend".equalsIgnoreCase(order);
            qw.orderBy(sort, asc);
        } else {
            // 默认排序，避免出现 ORDER BY  DESC
            qw.orderBy("createTime", false);
        }
        return qw;
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        final String salt="Ashen";
        return DigestUtils.md5DigestAsHex((salt+userPassword).getBytes());
    }

    @Override
    public String getDefaultAvatar(String userAccount) {
        String seed = DigestUtils.md5DigestAsHex(userAccount.getBytes());
        return UserConstant.USER_ACCOUNT_AVATAR + seed;
    }

    private static String generateDefaultUserName() {
        int prefixLength = ThreadLocalRandom.current()
                .nextInt(USER_NAME_PREFIX_MIN_LENGTH, USER_NAME_PREFIX_MAX_LENGTH + 1);
        return randomChars(ENGLISH_LETTERS, prefixLength)
                + "#"
                + randomChars(LETTERS_AND_DIGITS, USER_NAME_SUFFIX_LENGTH);
    }

    private static String randomChars(String candidates, int length) {
        StringBuilder builder = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            builder.append(candidates.charAt(random.nextInt(candidates.length())));
        }
        return builder.toString();
    }


}
