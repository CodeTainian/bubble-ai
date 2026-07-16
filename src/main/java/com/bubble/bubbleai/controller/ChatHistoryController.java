package com.bubble.bubbleai.controller;

import com.bubble.bubbleai.annotation.AuthCheck;
import com.bubble.bubbleai.common.BaseResponse;
import com.bubble.bubbleai.common.ResultUtils;
import com.bubble.bubbleai.constant.UserConstant;
import com.bubble.bubbleai.exception.ErrorCode;
import com.bubble.bubbleai.exception.ThrowUtils;
import com.bubble.bubbleai.model.dto.chathistory.ChatHistoryQueryRequest;
import com.bubble.bubbleai.model.entity.ChatHistory;
import com.bubble.bubbleai.model.vo.ChatHistoryVO;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.service.ChatHistoryService;
import com.bubble.bubbleai.service.UserService;

import java.time.LocalDateTime;

/**
 * 对话历史 控制层。
 *
 * @author  <a href="https://github.com/CodeTainian"></a>
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistoryVO>> listAppChatHistory(@PathVariable Long appId,
                                                                @RequestParam(defaultValue = "10") int pageSize,
                                                                @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistoryVO> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询所有对话历史
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listChatHistoryByPageForAdmin(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<ChatHistory> chatHistoryVOPage = chatHistoryService.listChatHistoryByPageForAdmin(chatHistoryQueryRequest);
        return ResultUtils.success(chatHistoryVOPage);
    }

}
