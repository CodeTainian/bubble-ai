package com.bubble.bubbleai.controller;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.annotation.AuthCheck;
import com.bubble.bubbleai.common.BaseResponse;
import com.bubble.bubbleai.common.DeleteRequest;
import com.bubble.bubbleai.common.ResultUtils;
import com.bubble.bubbleai.constant.UserConstant;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.exception.ThrowUtils;
import com.bubble.bubbleai.model.dto.app.AppAddRequest;
import com.bubble.bubbleai.model.dto.app.AppQueryRequest;
import com.bubble.bubbleai.model.dto.app.AppUpdateRequest;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.vo.AppVO;
import com.bubble.bubbleai.service.AppService;
import com.bubble.bubbleai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用 控制层。
 *
 * @author  <a href="https://github.com/liyupi">Coder-Ashely</a>
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    /**
     * 创建应用
     * @param appAddRequest 应用添加请求
     * @param request HTTP请求
     * @return 应用ID
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        String appName = appAddRequest.getAppName();
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StringUtils.isBlank(appName), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始 Prompt 不能为空");

        User loginUser = userService.getLoginUser(request);
        App app = new App();
        BeanUtils.copyProperties(appAddRequest,app);
        app.setUserId(loginUser.getId());
        app.setAppName(initPrompt.substring(0,Math.min(initPrompt.length(),12)));//前12位作为应用名称
        //Todo 是否需要处理默认优先级  app.setPriority(0); // 默认优先级为0
        app.setCodeGenType(CodeGenTypeEnum.MULTI_FIlE.getValue());
        boolean result = appService.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(app.getId());
    }

    /**
     * 根据 id 修改自己的应用（目前只支持修改应用名称）
     * @param appUpdateRequest 应用更新请求
     * @param request HTTP请求
     * @return 更新结果
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        Long appId = appUpdateRequest.getId();
        String appName = appUpdateRequest.getAppName();
        ThrowUtils.throwIf(StringUtils.isBlank(appName), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        // 验证应用所有权，只有自己和管理员可以修改
        App app = appService.validateAppOwnership(appId, request);
        app.setAppName(appName);
        app.setEditTime(LocalDateTime.now());//设置编辑时间
        boolean updated = appService.updateById(app);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 删除自己的应用
     * @param deleteRequest 删除请求
     * @param request HTTP请求
     * @return 删除结果
     */
    @PostMapping("/delete/my")
    public BaseResponse<Boolean> deleteMyApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        Long appId = deleteRequest.getId();
        App oldApp = appService.getById(appId);
        ThrowUtils.throwIf(oldApp==null,ErrorCode.NOT_FOUND_ERROR);//判断是否存在
        // 验证应用所有权
        appService.validateAppOwnership(appId, request);
        boolean removed = appService.removeById(appId);
        return ResultUtils.success(removed);
    }

    /**
     * 根据 id 查看应用详情
     * @param id 应用ID
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页查询自己的应用列表（支持根据名称查询，每页最多 20 个）
     * @param appQueryRequest 查询请求
     * @param request HTTP请求
     * @return 应用列表
     */
    @PostMapping("/list/page/my")
    public BaseResponse<Page<AppVO>> listMyAppByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多20个
        if (appQueryRequest.getPageSize() > 20) {
            appQueryRequest.setPageSize(20);
        }

        User loginUser = userService.getLoginUser(request);
        appQueryRequest.setUserId(loginUser.getId());

        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), appService.getQueryWrapper(appQueryRequest));

        // 数据脱敏
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页查询精选的应用列表（支持根据名称查询，每页最多 20 个）
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/list/page/featured")
    public BaseResponse<Page<AppVO>> listFeaturedAppByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多20个
        if (appQueryRequest.getPageSize() > 20) {
            appQueryRequest.setPageSize(20);
        }

        appQueryRequest.setIsFeatured(true);

        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), appService.getQueryWrapper(appQueryRequest));

        // 数据脱敏
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 【管理员】根据 id 删除任意应用
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    @AuthCheck(mustRule = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean removed = appService.removeById(deleteRequest.getId());
        return ResultUtils.success(removed);
    }

    /**
     * 【管理员】根据 id 更新任意应用（支持更新应用名称、应用封面、优先级）
     * @param appUpdateRequest 应用更新请求
     * @return 更新结果
     */
    @PostMapping("/update")
    @AuthCheck(mustRule = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        App app = new App();
        BeanUtils.copyProperties(appUpdateRequest, app);
        boolean updated = appService.updateById(app);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 【管理员】分页查询应用列表（支持根据除时间外的任何字段查询，每页数量不限）
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRule = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), appService.getQueryWrapper(appQueryRequest));
        // 数据脱敏
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 【管理员】根据 id 查看应用详情
     * @param id 应用ID
     * @return 应用详情
     */
    @GetMapping("/get")
    @AuthCheck(mustRule = UserConstant.ADMIN_ROLE)
    public BaseResponse<App> getAppById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(app);
    }
}

