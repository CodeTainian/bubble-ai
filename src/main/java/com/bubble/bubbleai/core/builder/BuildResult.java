package com.bubble.bubbleai.core.builder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Structured command result used by build diagnostics and the repair loop.
 */
public record BuildResult(
        boolean success,
        String stage,
        int exitCode,
        String command,
        String stdout,
        String stderr,
        String summary,
        long durationMillis,
        boolean timedOut
) {

    public BuildResult {
        stage = safe(stage);
        command = safe(command);
        stdout = safe(stdout);
        stderr = safe(stderr);
        summary = safe(summary);
        durationMillis = Math.max(0, durationMillis);
    }

    public static BuildResult success(String stage, String command, String stdout, String stderr, long durationMillis) {
        return new BuildResult(true, stage, 0, command, stdout, stderr, "", durationMillis, false);
    }

    public static BuildResult failure(String stage, String command, int exitCode,
                                      String stdout, String stderr, String summary,
                                      long durationMillis, boolean timedOut) {
        return new BuildResult(false, stage, exitCode, command, stdout, stderr,
                summary, durationMillis, timedOut);
    }

    /** Legacy factory retained for existing non-command validation callers. */
    public static BuildResult success(String command, String output) {
        return success("build", command, output, "", 0);
    }

    /** Legacy factory retained for existing non-command validation callers. */
    public static BuildResult failure(String command, int exitCode, String output, String summary) {
        return failure("validation", command, exitCode, output, "", summary, 0, false);
    }

    public String output() {
        if (stdout.isBlank()) {
            return stderr;
        }
        if (stderr.isBlank()) {
            return stdout;
        }
        return "[stdout]\n" + stdout + "\n[stderr]\n" + stderr;
    }

    public String errorSummary() {
        return summary;
    }

    public String abbreviatedOutput(int maxLength) {
        String output = output();
        if (output.isBlank()) {
            return "无输出";
        }
        if (maxLength <= 0 || output.length() <= maxLength) {
            return output;
        }
        int headLength = Math.min(maxLength / 4, 2000);
        int tailLength = maxLength - headLength;
        return output.substring(0, headLength)
                + "\n... 日志已截断 ...\n"
                + output.substring(output.length() - tailLength);
    }

    public String errorHash() {
        String normalized = (stage + "\n" + exitCode + "\n" + summary + "\n" + output())
                .replaceAll("\\u001B\\[[;\\d]*m", "")
                .replaceAll("\\s+", " ")
                .trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ignored) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
