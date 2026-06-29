package com.bubble.bubbleai.exception;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.guardrail.InputGuardrailException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes errors that need to be delivered through an SSE chat stream.
 */
public final class SseErrorMessageUtils {

    private static final Pattern GUARDRAIL_MESSAGE_PATTERN =
            Pattern.compile("failed with this message:\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SseErrorMessageUtils() {}

    public static int resolveCode(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return businessException.getCode();
        }
        if (error instanceof InputGuardrailException) {
            return ErrorCode.PARAMS_ERROR.getCode();
        }
        return ErrorCode.SYSTEM_ERROR.getCode();
    }

    public static String resolveMessage(Throwable error) {
        if (error instanceof BusinessException) {
            return StrUtil.blankToDefault(error.getMessage(), ErrorCode.SYSTEM_ERROR.getMessage());
        }
        if (error instanceof InputGuardrailException) {
            String guardrailMessage = extractGuardrailMessage(error.getMessage());
            if (StrUtil.isNotBlank(guardrailMessage)) {
                return guardrailMessage;
            }
        }
        return ErrorCode.SYSTEM_ERROR.getMessage();
    }

    private static String extractGuardrailMessage(String message) {
        if (StrUtil.isBlank(message)) {
            return message;
        }
        Matcher matcher = GUARDRAIL_MESSAGE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : message;
    }
}
