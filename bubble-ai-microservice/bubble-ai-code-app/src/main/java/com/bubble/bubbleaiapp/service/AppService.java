package com.bubble.bubbleaiapp.service;

import com.bubble.bubbleai.model.dto.app.AppAddRequest;
import com.bubble.bubbleai.model.dto.app.AppQueryRequest;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author  <a href="https://github.com/CodeTainian"></a>
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

    /**
     * AI生成的代码与用户的应用绑定
     * @param appId 应用ID
     * @param displayMessage 用户输入框中的原始消息
     * @param modelMessage 追加结构化上下文后的模型消息
     * @param metadata 内部结构化上下文
     * @param loginUser 登录用户
     * @return 流失响应内容
     */
    Flux<String> chatToGenCode(Long appId, String displayMessage, String modelMessage,
                               String metadata, User loginUser);

    /**
     * 继续监听正在生成的应用代码
     * @param appId 应用ID
     * @param loginUser 登录用户
     * @return 流式响应内容
     */
    Flux<String> watchGeneratingCode(Long appId, User loginUser);

    /**
     * 查询应用是否正在生成
     * @param appId 应用ID
     * @param loginUser 登录用户
     * @return 是否正在生成
     */
    Boolean isGenerating(Long appId, User loginUser);

    /**
     * 应用部署
     * @param appId 应用ID
     * @param loginUser 登录用户
     * @return url
     */
    String deployApp(Long appId,User loginUser);

    /**
     * 重新构建应用预览
     * @param appId 应用ID
     * @param loginUser 登录用户
     * @return 是否成功触发构建
     */
    Boolean rebuildApp(Long appId, User loginUser);


    /**
     * 创建应用
     * @param appAddRequest;
     * @param loginUser;
     * @return ;
     */
    Long creatApp(AppAddRequest appAddRequest,User loginUser);

}
