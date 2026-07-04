package com.bubble.bubbleaiapp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.AiCodeGenTypeRoutingService;
import com.bubble.bubbleaiapp.ai.AiCodeGenTypeRoutingServiceFactory;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.innerservice.InnerUserService;
import com.bubble.bubbleaiapp.core.AiCodeGeneratorFacade;
import com.bubble.bubbleaiapp.core.builder.ReactProjectBuilder;
import com.bubble.bubbleaiapp.core.handler.AppCoverGenerator;
import com.bubble.bubbleaiapp.core.handler.GenerationTaskManager;
import com.bubble.bubbleaiapp.core.handler.StreamHandlerExecute;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.exception.SseErrorMessageUtils;
import com.bubble.bubbleai.exception.ThrowUtils;
import com.bubble.bubbleaiapp.mapper.AppMapper;
import com.bubble.bubbleai.model.dto.app.AppAddRequest;
import com.bubble.bubbleai.model.dto.app.AppQueryRequest;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.model.vo.AppVO;
import com.bubble.bubbleai.model.vo.UserVO;
import com.bubble.bubbleai.monitor.MonitorContext;
import com.bubble.bubbleai.monitor.MonitorContextHolder;
import com.bubble.bubbleaiapp.service.AppService;
import com.bubble.bubbleaiapp.service.ChatHistoryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author  <a href="https://github.com/CodeTainian"></a>
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {
    @DubboReference
    private InnerUserService userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private StreamHandlerExecute streamHandlerExecute;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private AppCoverGenerator appCoverGenerator;
    @Resource
    private ReactProjectBuilder reactProjectBuilder;
    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;
    @Resource
    private GenerationTaskManager generationTaskManager;
    @Value("${code.deploy-host:http://localhost:8080/api/static}")
    private String deployHost;

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
        //通过将用户ID放在集合中，从而避免N+1查询问题
        Set<Long> UserIds = appList.stream().map(App::getUserId).collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(UserIds).stream().
                collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSort();
        String sortOrder = appQueryRequest.getOrder();

        return QueryWrapper.create().eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .eq("isDelete", 0)
                .orderBy(sortField,"ascend".equals(sortOrder));

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
        User loginUser = InnerUserService.getLoginUser(request);
        //只有本人操作或者是管理员可以修改
        if (!"admin".equals(loginUser.getUserRole())&&!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        }
        return app;
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR,"应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR,"用户消息不能为空");
        App app = validateGenerationAccess(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = getCodeGenType(app);
        Optional<Flux<String>> runningFlux = generationTaskManager.getTaskFlux(appId);
        if (runningFlux.isPresent()) {
            return runningFlux.get();
        }
        //5.保存用户消息
        chatHistoryService.addChatMessage(appId, loginUser.getId(),
                ChatHistoryMessageTypeEnum.USER.getValue(), message, null);
        //6设置监控上下文
        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId(loginUser.getId().toString())
                .appId(appId.toString())
                .build());
        //7.调用AI生成代码
        try {
            Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
            Flux<String> handledStream =
                    streamHandlerExecute.doExecute(codeStream, appId, loginUser, codeGenTypeEnum).
                            doFinally(signalType -> {MonitorContextHolder.clearContext();});//流程结束时清理(无论成功与否)
            return generationTaskManager.start(appId, handledStream);
        } catch (RuntimeException error) {
            saveGenerationErrorMessage(appId, loginUser.getId(), error);
            throw error;
        }

    }

    @Override
    public Flux<String> watchGeneratingCode(Long appId, User loginUser) {
        validateGenerationAccess(appId, loginUser);
        return generationTaskManager.getTaskFlux(appId).orElseGet(Flux::empty);
    }

    @Override
    public Boolean isGenerating(Long appId, User loginUser) {
        validateGenerationAccess(appId, loginUser);
        return generationTaskManager.isRunning(appId);
    }

    private App validateGenerationAccess(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR,"应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app==null,ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限访问该应用");
        }
        return app;
    }

    private CodeGenTypeEnum getCodeGenType(App app) {
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的应用生成类型");
        }
        return codeGenTypeEnum;
    }

    private void saveGenerationErrorMessage(Long appId, Long userId, Throwable error) {
        try {
            chatHistoryService.addChatMessage(
                    appId,
                    userId,
                    ChatHistoryMessageTypeEnum.ERROR.getValue(),
                    "AI 回复失败：" + SseErrorMessageUtils.resolveMessage(error),
                    null
            );
        } catch (Exception e) {
            log.error("save sync AI error chat history failed, appId={}", appId, e);
        }
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf(appId==null||appId<0,ErrorCode.PARAMS_ERROR,"应用ID不能为空");
        ThrowUtils.throwIf(loginUser==null,ErrorCode.NOT_LOGIN_ERROR,"用户未登录");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app==null,ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        //3.权限校验，仅本人可以部署自己的应用
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"用户无访问权限");
        }
        //4.检查是否已有deployKey
        String deployKey = app.getDeployKey();
        //5.没有则生成6位大小写字母+数字
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        //6.获取代码生成类型,构建源项目路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR+ File.separator +sourceDirName;
        //7.检查原目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists()||!sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"应用代码不存在，请先生成代码");
        }
        //8. Vue项目特殊处理：执行构造
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum==CodeGenTypeEnum.REACT_PROJECT){
            boolean buildResult = reactProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildResult,ErrorCode.SYSTEM_ERROR,"React项目构建失败，请重试");
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(),ErrorCode.SYSTEM_ERROR,"React项目构建完毕，但未生成dist目录");
            sourceDir = distDir;
        }
        //9.复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR+ File.separator +deployKey;
        try {
            FileUtil.copyContent(sourceDir,new File(deployDirPath),true);
        }catch (Exception e){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"部署失败"+e.getMessage());
        }
        //10.更新应用的deployKey和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult,ErrorCode.OPERATION_ERROR,"更新应用部署信息失败");
        //11.返回可访问的url
       // return AppConstant.CODE_DEPLOY_HOST + File.separator + deployKey;
        return String.format("%s/%s/", StrUtil.removeSuffix(deployHost, "/"), deployKey);

    }

    @Override
    public Boolean rebuildApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId==null||appId<=0,ErrorCode.PARAMS_ERROR,"应用ID不能为空");
        ThrowUtils.throwIf(loginUser==null,ErrorCode.NOT_LOGIN_ERROR,"用户未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app==null,ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"用户无访问权限");
        }

        CodeGenTypeEnum codeGenTypeEnum = resolvePreviewBuildType(app, appId);
        String sourceDirName = codeGenTypeEnum.getValue() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"应用代码不存在，请先生成代码");
        }

        if (CodeGenTypeEnum.REACT_PROJECT.equals(codeGenTypeEnum)) {
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCodeGenType(CodeGenTypeEnum.REACT_PROJECT.getValue());
            boolean updateResult = this.updateById(updateApp);
            ThrowUtils.throwIf(!updateResult,ErrorCode.OPERATION_ERROR,"更新应用生成类型失败");
            reactProjectBuilder.buildProjectAsync(sourceDirPath)
                    .thenAccept(buildSuccess -> {
                        if (Boolean.TRUE.equals(buildSuccess)) {
                            appCoverGenerator.generateAsync(appId, codeGenTypeEnum);
                        } else {
                            log.warn("rebuild React project failed, appId={}", appId);
                        }
                    })
                    .exceptionally(error -> {
                        log.error("rebuild React project failed, appId={}", appId, error);
                        return null;
                    });
            return true;
        }

        appCoverGenerator.generateAsync(appId, codeGenTypeEnum);
        return true;
    }

    private CodeGenTypeEnum resolvePreviewBuildType(App app, Long appId) {
        File reactProjectDir = new File(
                AppConstant.CODE_OUTPUT_ROOT_DIR,
                CodeGenTypeEnum.REACT_PROJECT.getValue() + "_" + appId
        );
        if (reactProjectDir.exists() && reactProjectDir.isDirectory()) {
            return CodeGenTypeEnum.REACT_PROJECT;
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的应用生成类型");
        }
        return codeGenTypeEnum;
    }

    @Override
    public Long creatApp(AppAddRequest appAddRequest, User loginUser) {
        //参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt),ErrorCode.PARAMS_ERROR,"");
        //构造入库对象
        App app = new App();
        BeanUtils.copyProperties(appAddRequest,app);
        app.setUserId(loginUser.getId());
        //应用名称暂时为initPrompt前12位
        app.setAppName(initPrompt.substring(0,Math.min(initPrompt.length(),12)));
        //使用AI智能选择代码生成类型
        AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodegenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodegenType.getValue());
        //插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {},类型: {}",app.getId(),selectedCodegenType.getValue());
        return app.getId();
    }

    /**
     * 删除应用时关联逻辑删除对话历史，应用物理资源由定时任务清理
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.removeByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }

}
