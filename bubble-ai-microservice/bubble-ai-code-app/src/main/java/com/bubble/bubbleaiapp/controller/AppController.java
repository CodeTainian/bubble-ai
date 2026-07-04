package com.bubble.bubbleaiapp.controller;

import cn.hutool.json.JSONUtil;
import com.bubble.bubbleai.annotation.AuthCheck;
import com.bubble.bubbleai.common.BaseResponse;
import com.bubble.bubbleai.common.DeleteRequest;
import com.bubble.bubbleai.common.ResultUtils;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.constant.UserConstant;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.exception.SseErrorMessageUtils;
import com.bubble.bubbleai.exception.ThrowUtils;
import com.bubble.bubbleai.innerservice.InnerUserService;
import com.bubble.bubbleai.model.dto.app.*;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.vo.AppVO;
import com.bubble.bubbleaiapp.ratelimiter.annotation.RateLimit;
import com.bubble.bubbleaiapp.ratelimiter.enmus.RateLimitType;
import com.bubble.bubbleaiapp.service.AppService;
import com.bubble.bubbleaiapp.service.ProjectDownloadService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author  <a href="https://github.com/CodeTainian"></a>
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private ProjectDownloadService projectDownloadService;

    /**
     * 创建应用
     * @param appAddRequest 应用添加请求
     * @param request HTTP请求
     * @return 应用ID
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = InnerUserService.getLoginUser(request);
        Long appId = appService.creatApp(appAddRequest,loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 删除应用（用户只能删除自己的应用）
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    @CacheEvict(value = "good_app_page", allEntries = true)
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = InnerUserService.getLoginUser(request);
        Long id = deleteRequest.getId();
        //判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        //仅本人或者管理员可以删除
        if (!oldApp.getUserId().equals(loginUser.getId())&&!UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean removed = appService.removeById(id);
        return ResultUtils.success(removed);
    }


    /**
     * 更新应用（用户只能更新自己的应用名称）
     * @param appUpdateRequest 应用更新请求
     * @return 更新结果
     */
    @PostMapping("/update")
    @CacheEvict(value = "good_app_page", allEntries = true)
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = InnerUserService.getLoginUser(request);
        Long id = appUpdateRequest.getId();
        //判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        //仅本人可更新
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw  new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        //设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
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
        //获取包含用户信息的封装类 可以方便展示是那个用户创建的应用
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页查询用户自己的应用列表
     * @param appQueryRequest 查询请求
     * @param request HTTP请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = InnerUserService.getLoginUser(request);
        int pageSize = appQueryRequest.getPageSize();
        // 限制每页最多20个
        if (pageSize > 20) {
            appQueryRequest.setPageSize(20);
        }
        //只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        int pageNum = appQueryRequest.getPageNum();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize),queryWrapper);

        // 数据脱敏
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页查询精选的应用列表
     * @param appQueryRequest 查询请求
     * @return 应用列表
     * unless 替代 condition的原因: 在方法执行后判断，此时参数已经解析完成;否则缓存拦截器比方法先执行，导致缓存失败
     */
    @PostMapping("/good/list/post/vo")
    @Cacheable(
            value = "good_app_page",
            key = "T(com.bubble.bubbleai.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            unless = "#appQueryRequest.pageNum <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageSize = appQueryRequest.getPageSize();
        // 限制每页最多20个
        if (pageSize > 20) {
            appQueryRequest.setPageSize(20);
        }
        //只查精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        int pageNum = appQueryRequest.getPageNum();
        //分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);

        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @CacheEvict(value = "good_app_page", allEntries = true)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     * @param appAdminUpdateRequest 管理员更新应用请求参数
     * @return  结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @CacheEvict(value = "good_app_page", allEntries = true)
    public  BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() ==null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = appAdminUpdateRequest.getId();
        //判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtils.copyProperties(appAdminUpdateRequest, app);
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页获取应用列表
     * @param appQueryRequest 管理员分页请求参数
     * @return 结果
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageSize = appQueryRequest.getPageSize();
        int pageNum = appQueryRequest.getPageNum();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 应用聊天生成代码(流式SSE)
     * @param appId 应用ID
     * @param message 用户消息
     * @param request 请求对象
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/code",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId, @RequestParam String message, HttpServletRequest request) {
        //参数校验
        ThrowUtils.throwIf(appId==null||appId <= 0, ErrorCode.PARAMS_ERROR,"应用ID无效");
        ThrowUtils.throwIf(StringUtils.isBlank(message), ErrorCode.PARAMS_ERROR,"用户消息不能为空");
        //获取当前登录用户
        User loginUser = InnerUserService.getLoginUser(request);
        //调用服务器生成代码(流式)
        Flux<String> contentFlux;
        try {
            contentFlux = appService.chatToGenCode(appId, message, loginUser);
        } catch (Throwable error) {
            return Flux.just(buildBusinessErrorEvent(error), buildDoneEvent());
        }
        //转换为ServerSentEvent格式
        return toSse(contentFlux);
    }

    /**
     * 继续监听正在生成的代码流，用于页面刷新后恢复连接。
     * @param appId 应用ID
     * @param request 请求对象
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> watchGeneratingCode(@RequestParam Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId==null||appId <= 0, ErrorCode.PARAMS_ERROR,"应用ID无效");
        User loginUser = InnerUserService.getLoginUser(request);
        Flux<String> contentFlux;
        try {
            contentFlux = appService.watchGeneratingCode(appId, loginUser);
        } catch (Throwable error) {
            return Flux.just(buildBusinessErrorEvent(error), buildDoneEvent());
        }
        return toSse(contentFlux);
    }

    /**
     * 查询应用是否仍有后台生成任务。
     * @param appId 应用ID
     * @param request 请求对象
     * @return 是否正在生成
     */
    @GetMapping("/chat/gen/status")
    public BaseResponse<Boolean> getGenerationStatus(@RequestParam Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId==null||appId <= 0, ErrorCode.PARAMS_ERROR,"应用ID无效");
        User loginUser = InnerUserService.getLoginUser(request);
        return ResultUtils.success(appService.isGenerating(appId, loginUser));
    }

    private Flux<ServerSentEvent<String>> toSse(Flux<String> contentFlux) {
        return contentFlux.map(chunk->{
            Map<String,String> wrapper = Map.of("d",chunk);
            String jsonData = JSONUtil.toJsonStr(wrapper);
            return ServerSentEvent.<String>builder().data(jsonData).build();
        }).onErrorResume(error -> Flux.just(buildBusinessErrorEvent(error)))
                .concatWith(Mono.just(buildDoneEvent()));
    }

    private ServerSentEvent<String> buildBusinessErrorEvent(Throwable error) {
        Map<String, Object> errorData = Map.of(
                "error", true,
                "code", SseErrorMessageUtils.resolveCode(error),
                "message", SseErrorMessageUtils.resolveMessage(error)
        );
        return ServerSentEvent.<String>builder()
                .event("business-error")
                .data(JSONUtil.toJsonStr(errorData))
                .build();
    }

    private ServerSentEvent<String> buildDoneEvent() {
        return ServerSentEvent.<String>builder().event("done").data("").build();
    }

    /**
     * 应用部署
     * @param appDeployRequest 部署请求
     * @param request 请求
     * @return 部署URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest,
                                       HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId==null||appId<=0,ErrorCode.PARAMS_ERROR,"应用ID不能为空");
        //获取当前登录用户
        User loginUser = InnerUserService.getLoginUser(request);
        //调用服务部署应用
        String deployedAppUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployedAppUrl);
    }

    /**
     * 重新构建应用预览
     * @param appDeployRequest 应用请求
     * @param request 请求
     * @return 是否成功触发构建
     */
    @PostMapping("/rebuild")
    public BaseResponse<Boolean> rebuildApp(@RequestBody AppDeployRequest appDeployRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId==null||appId<=0,ErrorCode.PARAMS_ERROR,"应用ID不能为空");
        User loginUser = InnerUserService.getLoginUser(request);
        Boolean result = appService.rebuildApp(appId, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 下载应用代码
     * @param appId 应用ID
     * @param request 请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId, HttpServletRequest request, HttpServletResponse response){
        ThrowUtils.throwIf(appId==null||appId<=0,ErrorCode.PARAMS_ERROR,"应用ID无效");
        //查询应用信息
        App app = appService.getById(appId);
        //权限校验，只有应用创建者可以下载代码
        ThrowUtils.throwIf(app==null,ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        User loginUser = InnerUserService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有下载权限");
        }
        //创建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType+"_"+appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR+ File.separator+sourceDirName;
        //检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists()||!sourceDir.isDirectory(),ErrorCode.NOT_FOUND_ERROR,"应用代码不存在");
        //生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);
        //调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath,downloadFileName,response);
    }

}
