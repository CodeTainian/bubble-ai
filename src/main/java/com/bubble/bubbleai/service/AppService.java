package com.bubble.bubbleai.service;

import com.bubble.bubbleai.model.dto.app.AppQueryRequest;
import com.bubble.bubbleai.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.bubble.bubbleai.model.entity.App;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author  <a href="https://github.com/liyupi">Coder-Ashely</a>
 */
public interface AppService extends IService<App> {

    /**
     * 获取脱敏后的单个应用
     * @param app 应用实体
     * @return 脱敏后的应用
     */
    AppVO getAppVO(App app);

    /**
     * 获取脱敏后的应用列表
     * @param appList 应用列表
     * @return 脱敏后的应用列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 分页查询应用
     * @param appQueryRequest 请求对象
     * @return 封装好的查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 验证应用是否属于当前用户
     * @param appId 应用ID
     * @param request HTTP请求
     * @return 应用实体
     */
    App validateAppOwnership(Long appId, HttpServletRequest request);
}

