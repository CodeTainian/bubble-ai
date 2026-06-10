package com.bubble.bubbleai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.model.enums.ChatStreamMessageTypeEnum;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.constant.CaptureConstant;
import com.bubble.bubbleai.core.AiCodeGeneratorFacade;
import com.bubble.bubbleai.core.SSE.ChatStreamMessageFactory;
import com.bubble.bubbleai.core.SSE.ChatStreamSseUtil;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.exception.ThrowUtils;
import com.bubble.bubbleai.model.dto.app.AppQueryRequest;
import com.bubble.bubbleai.model.dto.toolCall.ChatStreamMessage;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleai.model.entity.ChatHistory;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.model.vo.AppVO;
import com.bubble.bubbleai.mapper.AppMapper;
import com.bubble.bubbleai.model.vo.UserVO;
import com.bubble.bubbleai.service.AppService;
import com.bubble.bubbleai.service.ChatHistoryService;
import com.bubble.bubbleai.service.ScreenshotService;
import com.bubble.bubbleai.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author  <a href="https://github.com/CodeTainian"></a>
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;
    @Autowired
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Autowired
    private ScreenshotService screenshotService;
    @Resource
    private ChatHistoryService chatHistoryService;

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
        User loginUser = userService.getLoginUser(request);
        //只有本人操作或者是管理员可以修改
        if (!"admin".equals(loginUser.getUserRole())&&!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        }
        return app;
    }

    @Override
    public Flux<ServerSentEvent<ChatStreamMessage>> chatToGenCode(Long appId, String message, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR,"应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR,"用户消息不能为空");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app==null,ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        //3.验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限访问该应用");
        }
        //4.获取应用的代码生成类型
        // 暂时统一设置为 React 工程生成
        String codeGenType = CodeGenTypeEnum.REACT_PROJECT.getValue();
        if (!codeGenType.equals(app.getCodeGenType())) {
            App updateCodeGenTypeApp = new App();
            updateCodeGenTypeApp.setId(appId);
            updateCodeGenTypeApp.setCodeGenType(codeGenType);
            boolean updateCodeGenTypeResult = this.updateById(updateCodeGenTypeApp);
            ThrowUtils.throwIf(!updateCodeGenTypeResult, ErrorCode.OPERATION_ERROR, "更新应用生成类型失败");
            app.setCodeGenType(codeGenType);
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的应用生成类型");
        }
        //5.保存用户消息
        ChatHistory userMessage = chatHistoryService.addChatMessage(
                appId,
                loginUser.getId(),
                ChatHistoryMessageTypeEnum.USER.getValue(),
                message,
                null
        );
        //6.调用AI生成代码
        Flux<ServerSentEvent<ChatStreamMessage>> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 收集 AI 正文和文件写入事件中的完整代码，供历史对话回放和上下文记忆使用。
        StringBuilder aiMessageBuilder = new StringBuilder();
        // 标记本次流是否出现错误
        AtomicBoolean hasError = new AtomicBoolean(false);
        // 记录错误信息，方便保存到聊天记录
        AtomicReference<String> errorMessageRef = new AtomicReference<>();
        return codeStream.doOnNext(event->{
            ChatStreamMessage streamMessage = event.data();
            if (streamMessage==null||streamMessage.getType()==null){
                return;
            }
            ChatStreamMessageTypeEnum type = streamMessage.getType();
            if (ChatStreamMessageTypeEnum.AI_RESPONSE.equals(type)){
                String content = streamMessage.getContent();
                if (StrUtil.isNotEmpty(content)){
                    aiMessageBuilder.append(content);
                }
                return;
            }
            if (ChatStreamMessageTypeEnum.FILE_WRITE.equals(type)){
                appendFileWriteToHistory(aiMessageBuilder, streamMessage);
                return;
            }
            if (ChatStreamMessageTypeEnum.ERROR.equals(type)){
                hasError.set(true);
                errorMessageRef.set(streamMessage.getContent());
            }
            // TOOL_CALL_START、TOOL_CALL_RESULT、THINKING、DONE 不保存进 AI 回复正文
        }).doOnComplete(()->{
            if (hasError.get()){
                String errorMessage = errorMessageRef.get();
                if (StrUtil.isBlank(errorMessage)){
                    errorMessage="Ai 回复失败";
                }
                chatHistoryService.addChatMessage(appId,
                        loginUser.getId(),
                        ChatHistoryMessageTypeEnum.ERROR.getValue(),
                        errorMessage,
                        userMessage.getId());
                return;
            }
            String aiMessage = aiMessageBuilder.toString();
            if (StrUtil.isNotBlank(aiMessage)) {
                chatHistoryService.addChatMessage(
                        appId,
                        loginUser.getId(),
                        ChatHistoryMessageTypeEnum.AI.getValue(),
                        aiMessage,
                        userMessage.getId()
                );
            }
            CompletableFuture.runAsync(()->{
                try {
                    generateAppCover(appId, codeGenType);
                }catch (Exception e){
                    log.error("generate app's cover failed = {}",appId,e);
                }
            });
        }).onErrorResume(e->{
            hasError.set(true);
            String errorMessage = e.getMessage();
            if (StrUtil.isBlank(errorMessage)) {
                errorMessage = e.getClass().getSimpleName();
            }
            chatHistoryService.addChatMessage(
                    appId,
                    loginUser.getId(),
                    ChatHistoryMessageTypeEnum.ERROR.getValue(),
                    "AI 回复失败：" + errorMessage,
                    userMessage.getId()
            );
            ServerSentEvent<ChatStreamMessage> errorEvent =
                    ChatStreamSseUtil.build(ChatStreamMessageFactory.error(appId,e));
            return Flux.just(errorEvent);
        });

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
        String sourceDirName = AppConstant.buildCodeOutputDirName(codeGenType, appId);
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR+ File.separator +sourceDirName;
        //7.检查原目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists()||!sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"应用代码不存在，请先生成代码");
        }
        //8.复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR+ File.separator +deployKey;
        try {
            FileUtil.copyContent(sourceDir,new File(deployDirPath),true);
        }catch (Exception e){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"部署失败"+e.getMessage());
        }
        //9.更新应用的deployKey和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult,ErrorCode.OPERATION_ERROR,"更新应用部署信息失败");
        //10.返回可访问的url
       // return AppConstant.CODE_DEPLOY_HOST + File.separator + deployKey;
        return String.format("%s/%s/",AppConstant.CODE_DEPLOY_HOST,deployKey);

    }

    public void generateAppCover(Long appId, String codeGenType) {
        // 1. spell the website index name that Ai generated
        String sourceDirName = AppConstant.buildCodeOutputDirName(codeGenType, appId);
        // 2. 拼接可以被浏览器访问的网站首页 URL
        String previewUrl = String.format("%s/%s/", CaptureConstant.CAPTURE_PREVIEW_COVER, sourceDirName);
        // eg："http://localhost:8123/api/static/";
        // 3. spell the cover image file name
        String coverFileName = sourceDirName + ".png";
        // 4. spell the ture cover image file name that save into the location
        String coverSavePath = CaptureConstant.CAPTURE_OUTPUT_COVER
                + File.separator
                + coverFileName;
        // eg：
        // /Users/codergu/myProject/bubble-ai/tmp/output_covers/html_123.png
        // 5. call screenshot service
        screenshotService.captureHomePage(previewUrl, coverSavePath);
        // 6.  spell the cover URL that the nginx service can be visited
        String coverUrl = CaptureConstant.CAPTURE_HOST
                + "/output_covers/"
                + coverFileName;
        // eg：
        // http://localhost:8080/output_covers/html_123.png
        // 7. update the datebase
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCover(coverUrl);
        this.updateById(updateApp);
    }

    private void appendFileWriteToHistory(StringBuilder aiMessageBuilder, ChatStreamMessage streamMessage) {
        String code = streamMessage.getInternalContent();
        if (code == null) {
            code = streamMessage.getContent();
        }
        if (code == null) {
            return;
        }
        String path = firstNotBlank(
                getMetadataString(streamMessage, "path"),
                getMetadataString(streamMessage, "relativeFilePath"),
                getMetadataString(streamMessage, "fileName")
        );
        String language = firstNotBlank(getMetadataString(streamMessage, "language"), guessLanguage(path));
        String step = getMetadataString(streamMessage, "step");
        String description = firstNotBlank(
                getMetadataString(streamMessage, "description"),
                StrUtil.isBlank(path) ? "创建文件" : "创建" + path
        );
        if (aiMessageBuilder.length() > 0) {
            aiMessageBuilder.append("\n\n");
        }
        aiMessageBuilder.append("STEP ")
                .append(StrUtil.isBlank(step) ? "0" : step)
                .append(": ")
                .append(description)
                .append("\n文件：")
                .append(StrUtil.isBlank(path) ? "生成文件" : path)
                .append("\n```")
                .append(language)
                .append("\n")
                .append(code);
        if (!code.endsWith("\n")) {
            aiMessageBuilder.append("\n");
        }
        aiMessageBuilder.append("```");
    }

    private String getMetadataString(ChatStreamMessage streamMessage, String key) {
        if (streamMessage.getMetadata() == null || streamMessage.getMetadata().get(key) == null) {
            return "";
        }
        return String.valueOf(streamMessage.getMetadata().get(key));
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String guessLanguage(String path) {
        if (path == null) {
            return "text";
        }
        if (path.endsWith(".jsx")) return "jsx";
        if (path.endsWith(".tsx")) return "tsx";
        if (path.endsWith(".js")) return "javascript";
        if (path.endsWith(".ts")) return "typescript";
        if (path.endsWith(".css")) return "css";
        if (path.endsWith(".json")) return "json";
        if (path.endsWith(".html")) return "html";
        return "text";
    }

    /**
     * 删除应用时关联删除对话历史
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
