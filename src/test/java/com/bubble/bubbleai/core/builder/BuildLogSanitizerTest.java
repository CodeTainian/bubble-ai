package com.bubble.bubbleai.core.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLogSanitizerTest {

    private final BuildLogSanitizer sanitizer = new BuildLogSanitizer();

    @Test
    void redactsSecretsAndAbsolutePathsAndKeepsTail() {
        String log = "Authorization: Bearer top-secret-token\n"
                + "apiKey=sk-proj-1234567890abcdefgh\n"
                + "/Users/server/app/react_project_1/src/App.tsx:4 error\n"
                + "x".repeat(200) + "TAIL_ERROR";

        String sanitized = sanitizer.sanitize(log, "/Users/server/app/react_project_1", 120);

        assertFalse(sanitized.contains("top-secret-token"));
        assertFalse(sanitized.contains("1234567890abcdefgh"));
        assertFalse(sanitized.contains("/Users/server"));
        assertTrue(sanitized.contains("TAIL_ERROR"));
    }
}
