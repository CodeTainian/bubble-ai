package com.bubble.bubbleai.service.impl;

import com.bubble.bubbleai.model.entity.ChatHistory;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.model.enums.ChatMessageSource;
import com.bubble.bubbleai.model.vo.ChatHistoryVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryServiceImplTest {

    private final ChatHistoryServiceImpl service = new ChatHistoryServiceImpl();

    @Test
    void legacyVisualContextIsRemovedFromDisplayOnly() {
        ChatHistory history = ChatHistory.builder()
                .messageType(ChatHistoryMessageTypeEnum.USER.getValue())
                .message("把这个按钮改成蓝色\n\n" + ChatHistoryServiceImpl.VISUAL_CONTEXT_MARKER
                        + "\n- 选择器：#secret-selector\n- 标签：button")
                .modelContent("full model content")
                .build();

        assertEquals("把这个按钮改成蓝色", service.sanitizeDisplayContent(history));
        ChatHistoryVO vo = service.getChatHistoryVO(history);
        assertNotNull(vo);
        assertEquals("把这个按钮改成蓝色", vo.getMessage());
        assertFalse(vo.getMessage().contains("selector"));
    }

    @Test
    void internalRowsAreNeverConvertedToUserHistory() {
        ChatHistory hidden = ChatHistory.builder()
                .messageType(ChatHistoryMessageTypeEnum.ERROR.getValue())
                .message("[internal]")
                .modelContent("npm output and repair prompt")
                .messageSource(ChatMessageSource.BUILD_ERROR.name())
                .visibleToUser(false)
                .build();

        assertFalse(service.isVisibleToUser(hidden));
        assertNull(service.getChatHistoryVO(hidden));
    }
}
