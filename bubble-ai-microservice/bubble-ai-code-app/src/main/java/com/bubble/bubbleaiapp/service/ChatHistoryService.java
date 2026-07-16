package com.bubble.bubbleaiapp.service;

import com.bubble.bubbleai.model.dto.chathistory.ChatHistoryQueryRequest;
import com.bubble.bubbleai.model.entity.ChatHistory;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.enums.ChatMessageSource;
import com.bubble.bubbleai.model.vo.ChatHistoryVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author  <a href="https://github.com/CodeTainian"></a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话消息
     * @param appId 应用 id
     * @param userId 用户 id
     * @param messageType 消息类型
     * @param message 消息内容
     * @param parentId 父消息 id
     * @return 对话历史
     */
    ChatHistory addChatMessage(Long appId, Long userId, String messageType, String message, Long parentId);

    ChatHistory addChatMessage(Long appId, Long userId, String messageType,
                               String displayContent, String modelContent,
                               ChatMessageSource source, boolean visibleToUser,
                               String metadata, Long parentId);

    default ChatHistory addInternalMessage(Long appId, Long userId, String messageType,
                                           String modelContent, ChatMessageSource source) {
        return addChatMessage(appId, userId, messageType, null, modelContent,
                source, false, null, null);
    }

    /**
     * 获取对话历史视图
     * @param chatHistory 对话历史
     * @return 对话历史视图
     */
    ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory);

    /**
     * select chat history by page
     * @param appId;
     * @param pageSize;
     * @param lastCreateTime;
     * @param loginUser;
     * @return ChatHistory
     */
    Page<ChatHistoryVO> listAppChatHistoryByPage(Long appId, int pageSize,
                                                 LocalDateTime lastCreateTime,
                                                 User loginUser);

    /**
     * 管理员分页查询所有对话历史
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    Page<ChatHistory> listChatHistoryByPageForAdmin(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 构造查询条件
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 删除应用关联的对话历史
     * @param appId 应用 id
     * @return 删除结果
     */
    boolean removeByAppId(Long appId);

    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory,int maxCount);

}
