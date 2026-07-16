package com.bubble.bubbleaiapp.core.builder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public record BuildResult(boolean success, String stage, int exitCode, String command,
                          String stdout, String stderr, String summary,
                          long durationMillis, boolean timedOut) {
    public BuildResult {
        stage = safe(stage); command = safe(command); stdout = safe(stdout);
        stderr = safe(stderr); summary = safe(summary); durationMillis = Math.max(0, durationMillis);
    }
    public static BuildResult success(String stage, String command, String out, String err, long duration) {
        return new BuildResult(true, stage, 0, command, out, err, "", duration, false);
    }
    public static BuildResult failure(String stage, String command, int code, String out, String err, String summary, long duration, boolean timeout) {
        return new BuildResult(false, stage, code, command, out, err, summary, duration, timeout);
    }
    public static BuildResult success(String command, String output) { return success("build", command, output, "", 0); }
    public static BuildResult failure(String command, int code, String output, String summary) { return failure("validation", command, code, output, "", summary, 0, false); }
    public String output() { return stdout.isBlank() ? stderr : stderr.isBlank() ? stdout : "[stdout]\n" + stdout + "\n[stderr]\n" + stderr; }
    public String errorSummary() { return summary; }
    public String abbreviatedOutput(int max) {
        String out = output();
        if (out.isBlank()) return "无输出";
        if (max <= 0 || out.length() <= max) return out;
        int head = Math.min(2000, max / 4);
        return out.substring(0, head) + "\n... 日志已截断 ...\n" + out.substring(out.length() - (max - head));
    }
    public String errorHash() {
        String value = (stage + exitCode + summary + output()).replaceAll("\\s+", " ").trim();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }
    private static String safe(String value) { return value == null ? "" : value; }
}
