-- Apply before deploying the corresponding application version.
-- Rollback: drop idx_app_visible_time and the five added columns. The legacy
-- message column remains untouched, so rollback does not delete chat records.

ALTER TABLE chat_history
    ADD COLUMN displayContent TEXT NULL COMMENT '用户界面安全展示内容' AFTER message,
    ADD COLUMN modelContent MEDIUMTEXT NULL COMMENT '发送给模型的完整内容，不得返回用户界面' AFTER displayContent,
    ADD COLUMN messageSource VARCHAR(32) NULL COMMENT '消息来源' AFTER messageType,
    ADD COLUMN visibleToUser TINYINT(1) NOT NULL DEFAULT 1 COMMENT '用户是否可见' AFTER messageSource,
    ADD COLUMN metadata TEXT NULL COMMENT '结构化内部上下文' AFTER visibleToUser;

UPDATE chat_history
SET displayContent = message,
    modelContent = message,
    messageSource = CASE
        WHEN messageType = 'user' THEN 'USER_INPUT'
        WHEN messageType = 'ai' THEN 'AI_OUTPUT'
        ELSE 'SYSTEM'
    END,
    visibleToUser = CASE WHEN messageType IN ('user', 'ai') THEN 1 ELSE 0 END
WHERE displayContent IS NULL
   OR modelContent IS NULL
   OR messageSource IS NULL;

CREATE INDEX idx_app_visible_time
    ON chat_history (appId, visibleToUser, createTime);
