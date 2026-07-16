package com.bubble.bubbleaiapp.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.constant.UserConstant;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.exception.ThrowUtils;
import com.bubble.bubbleai.innerservice.InnerUserService;
import com.bubble.bubbleaiapp.mapper.AppMapper;
import com.bubble.bubbleaiapp.mapper.ChatHistoryMapper;
import com.bubble.bubbleai.model.dto.chathistory.ChatHistoryQueryRequest;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleai.model.entity.ChatHistory;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.model.enums.ChatMessageSource;
import com.bubble.bubbleai.model.vo.AppVO;
import com.bubble.bubbleai.model.vo.ChatHistoryVO;
import com.bubble.bubbleai.model.vo.UserVO;
import com.bubble.bubbleaiapp.service.ChatHistoryService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 对话历史 服务层实现。
 *
 * @author  <a href="https://github.com/CodeTainian"></a>
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService{

    static final String VISUAL_CONTEXT_MARKER =
            "请优先基于用户在预览页面中选中的元素进行修改。选中元素信息如下：";

    @Resource
    private AppMapper appMapper;

   @DubboReference
    private InnerUserService userService;

    @Override
    public ChatHistory addChatMessage(Long appId, Long userId, String messageType, String message, Long parentId) {
        ChatHistoryMessageTypeEnum type = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ChatMessageSource source = type == ChatHistoryMessageTypeEnum.USER
                ? ChatMessageSource.USER_INPUT
                : type == ChatHistoryMessageTypeEnum.AI
                ? ChatMessageSource.AI_OUTPUT
                : ChatMessageSource.SYSTEM;
        return addChatMessage(appId, userId, messageType, message, message,
                source, type != ChatHistoryMessageTypeEnum.ERROR, null, parentId);
    }

    @Override
    public ChatHistory addChatMessage(Long appId, Long userId, String messageType,
                                      String displayContent, String modelContent,
                                      ChatMessageSource source, boolean visibleToUser,
                                      String metadata, Long parentId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(displayContent) && StrUtil.isBlank(modelContent),
                ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "消息类型错误");
        ThrowUtils.throwIf(source == null, ErrorCode.PARAMS_ERROR, "消息来源不能为空");
        String legacyMessage = StrUtil.isNotBlank(displayContent) ? displayContent : "[internal]";
        ChatHistory chatHistory =  ChatHistory.builder()
                .appId(appId)
                .message(legacyMessage)
                .displayContent(displayContent)
                .modelContent(StrUtil.blankToDefault(modelContent, displayContent))
                .messageSource(source.name())
                .visibleToUser(visibleToUser && source.isVisibleToUser())
                .metadata(metadata)
                .messageType(messageType)
                .userId(userId)
                .parentId(parentId)
                .build();
        boolean result = this.save(chatHistory);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存对话历史失败");
        return chatHistory;
    }

    @Override
    public ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory) {
        if (!isVisibleToUser(chatHistory)) {
            return null;
        }
        ChatHistoryVO chatHistoryVO = new ChatHistoryVO();
        BeanUtils.copyProperties(chatHistory, chatHistoryVO);
        chatHistoryVO.setMessage(sanitizeDisplayContent(chatHistory));
        chatHistoryVO.setVisibleToUser(true);
        Long userId = chatHistory.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            chatHistoryVO.setUser(userVO);
        }
        Long appId = chatHistory.getAppId();
        if (appId != null) {
            App app = appMapper.selectOneById(appId);
            AppVO appVO = getAppVO(app);
            chatHistoryVO.setApp(appVO);
        }
        return chatHistoryVO;
    }

    @Override
    public Page<ChatHistory> listChatHistoryByPageForAdmin(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = chatHistoryQueryRequest.getPageNum();
        int pageSize = chatHistoryQueryRequest.getPageSize();
        if (pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        if (pageSize > 50) {
            pageSize = 50;
        }
        QueryWrapper queryWrapper = getQueryWrapper(chatHistoryQueryRequest);
        return  this.page(Page.of(pageNum, pageSize), queryWrapper);

    }

    @Override
    public Page<ChatHistoryVO> listAppChatHistoryByPage(Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appMapper.selectOneById(appId);
//        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        queryWrapper
                .and("(visibleToUser = 1 OR visibleToUser IS NULL)")
                .ne("messageType", ChatHistoryMessageTypeEnum.ERROR.getValue())
                .and("(messageSource IS NULL OR messageSource IN ('USER_INPUT', 'AI_OUTPUT'))");
        // 查询数据
        Page<ChatHistory> historyPage = this.page(Page.of(1, pageSize), queryWrapper);
        Page<ChatHistoryVO> voPage = new Page<>(historyPage.getPageNumber(), historyPage.getPageSize(), historyPage.getTotalRow());
        voPage.setRecords(historyPage.getRecords().stream()
                .map(this::getChatHistoryVO)
                .filter(Objects::nonNull)
                .toList());
        return voPage;
    }

    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest==null){
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        Long parentId = chatHistoryQueryRequest.getParentId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sort = chatHistoryQueryRequest.getSort();
        String order = chatHistoryQueryRequest.getOrder();
        if (StringUtils.isNotBlank(messageType)
                && ChatHistoryMessageTypeEnum.getEnumByValue(messageType) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息类型错误");
        }
        queryWrapper
                .eq("id", id)
                .eq("appId", appId)
                .eq("userId", userId)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("parentId", parentId)
                .eq("isDelete", 0);
        if (lastCreateTime!=null){
            queryWrapper.lt("createTime",lastCreateTime);
        }
        if (StringUtils.isNotBlank(sort)) {
            queryWrapper.orderBy(sort,"ascend".equalsIgnoreCase(order));
        } else {
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    @Override
    public boolean removeByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create().eq("appId", appId);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count <= 0) {
            return true;
        }
        boolean removed = this.remove(queryWrapper);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "删除应用对话历史失败");
        return true;
    }

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId,appId)
                    .orderBy(ChatHistory::getCreateTime,false)
                    .limit(1,maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                chatMemory.clear();
                return 0;
            }
            //反转列表，确保按照时间顺序正序，老的在前面，新的在后
            historyList = historyList.reversed();
            //按时间顺序添加到记忆中
            int loadedCount = 0;
            //清理缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory chatHistory : historyList) {
                ChatMessageSource source = parseSource(chatHistory.getMessageSource());
                if (source != null && source != ChatMessageSource.USER_INPUT && source != ChatMessageSource.AI_OUTPUT) {
                    continue;
                }
                String modelContent = StrUtil.blankToDefault(chatHistory.getModelContent(), chatHistory.getMessage());
                if (ChatHistoryMessageTypeEnum.USER.getValue().
                        equals(chatHistory.getMessageType())){
                    chatMemory.add(UserMessage.from(modelContent));
                    loadedCount++;
                }else if (ChatHistoryMessageTypeEnum.AI.getValue().
                        equals(chatHistory.getMessageType())){
                    chatMemory.add(AiMessage.from(modelContent));
                    loadedCount++;
                }
            }
            log.info("成功为appId:{} 加载了 {} 条历史对话",appId,loadedCount);
            return loadedCount;
        }catch (Exception e){
            log.error("加载历史对话失败，appId:{},error:{}",appId,e.getMessage());
        }
        //加载失败不影响系统运行，没有历史上下文
        return 0;
    }

    String sanitizeDisplayContent(ChatHistory chatHistory) {
        String content = StrUtil.blankToDefault(chatHistory.getDisplayContent(), chatHistory.getMessage());
        if (!ChatHistoryMessageTypeEnum.USER.getValue().equals(chatHistory.getMessageType())) {
            return content;
        }
        int markerIndex = content.indexOf("\n\n" + VISUAL_CONTEXT_MARKER);
        if (markerIndex < 0) {
            markerIndex = content.indexOf("\r\n\r\n" + VISUAL_CONTEXT_MARKER);
        }
        return markerIndex > 0 ? content.substring(0, markerIndex).trim() : content;
    }

    boolean isVisibleToUser(ChatHistory chatHistory) {
        if (chatHistory == null || Boolean.FALSE.equals(chatHistory.getVisibleToUser())) {
            return false;
        }
        if (ChatHistoryMessageTypeEnum.ERROR.getValue().equals(chatHistory.getMessageType())) {
            return false;
        }
        ChatMessageSource source = parseSource(chatHistory.getMessageSource());
        return source == null || source == ChatMessageSource.USER_INPUT || source == ChatMessageSource.AI_OUTPUT;
    }

    private ChatMessageSource parseSource(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return ChatMessageSource.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ChatMessageSource.SYSTEM;
        }
    }

    private AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtils.copyProperties(app, appVO);
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            appVO.setUser(userService.getUserVO(user));
        }
        return appVO;
    }
}
