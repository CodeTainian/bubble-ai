package com.bubble.bubbleai.model.enums;

/**
 * Lifecycle of one code-generation request, including React build repair.
 */
public enum GenerationState {
    GENERATING,
    BUILDING,
    BUILD_FAILED,
    AI_REPAIRING,
    REPAIR_COMPLETED,
    SUCCESS,
    FAILED_MAX_ATTEMPTS
}
