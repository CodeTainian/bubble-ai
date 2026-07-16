package com.bubble.bubbleai.model.enums;

import lombok.Getter;

@Getter
public enum ChatMessageSource {
    USER_INPUT(true), AI_OUTPUT(true), SYSTEM(false), TOOL(false),
    VISUAL_CONTEXT(false), BUILD_ERROR(false), AUTO_REPAIR(false);

    private final boolean visibleToUser;

    ChatMessageSource(boolean visibleToUser) {
        this.visibleToUser = visibleToUser;
    }
}
