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
        BusinessException businessException = findCause(error, BusinessException.class);
        if (businessException != null) {
            return businessException.getCode();
        }
        if (findCause(error, InputGuardrailException.class) != null) {
            return ErrorCode.PARAMS_ERROR.getCode();
        }
        return ErrorCode.SYSTEM_ERROR.getCode();
    }

    public static String resolveMessage(Throwable error) {
        BusinessException businessException = findCause(error, BusinessException.class);
        if (businessException != null) {
            return StrUtil.blankToDefault(businessException.getMessage(), ErrorCode.SYSTEM_ERROR.getMessage());
        }
        InputGuardrailException inputGuardrailException = findCause(error, InputGuardrailException.class);
        if (inputGuardrailException != null) {
            String guardrailMessage = extractGuardrailMessage(inputGuardrailException.getMessage());
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

    private static <T extends Throwable> T findCause(Throwable error, Class<T> targetType) {
        Throwable current = error;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return targetType.cast(current);
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return null;
    }
}
