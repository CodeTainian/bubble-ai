package com.bubble.bubbleai.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.model.dto.app.AppQueryRequest;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.vo.AppVO;
import com.bubble.bubbleai.mapper.AppMapper;
import com.bubble.bubbleai.model.vo.UserVO;
import com.bubble.bubbleai.service.AppService;
import com.bubble.bubbleai.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author  <a href="https://github.com/liyupi">Coder-Ashely</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtils.copyProperties(app, appVO);
        //关联查询用户的信息
        Long userId = app.getUserId();
        if (userId!=null){
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        return appList.stream().map(this::getAppVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        Boolean isFeatured = appQueryRequest.getIsFeatured();

        QueryWrapper qw = QueryWrapper.create();
        qw.eq("id", id)
                .like("appName", appName)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .eq("isDelete", 0);

        // 精选应用：优先级大于0
        if (isFeatured != null && isFeatured) {
            qw.gt("priority", 0);
        }

        // 排序：白名单 + 空值兜底，ascend/descend 方向映射
        java.util.Set<String> allowSort = java.util.Set.of("id", "appName", "priority", "createTime", "updateTime", "editTime");
        String sort = appQueryRequest.getSort();
        String order = appQueryRequest.getOrder();
        if (StringUtils.isNotBlank(sort) && allowSort.contains(sort)) {
            boolean asc = "ascend".equalsIgnoreCase(order);
            qw.orderBy(sort, asc);
        } else {
            // 默认排序
            qw.orderBy("createTime", false);
        }
        return qw;
    }

    @Override
    public App validateAppOwnership(Long appId, HttpServletRequest request) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        App app = this.getById(appId);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }
        User loginUser = userService.getLoginUser(request);
        //只有本人操作或者是管理员可以修改
        if (!"admin".equals(loginUser.getUserRole())&&!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        }
        return app;
    }
}

