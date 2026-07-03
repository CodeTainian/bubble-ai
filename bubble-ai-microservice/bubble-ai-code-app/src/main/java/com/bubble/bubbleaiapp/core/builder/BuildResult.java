package com.bubble.bubbleaiapp.core.builder;

/**
 * React project build result with enough context for diagnostics and repair.
 */
public record BuildResult(
        boolean success,
        String command,
        int exitCode,
        String output,
        String errorSummary
) {

    public BuildResult {
        command = command == null ? "" : command;
        output = output == null ? "" : output;
        errorSummary = errorSummary == null ? "" : errorSummary;
    }

    public static BuildResult success(String command, String output) {
        return new BuildResult(true, command, 0, output, "");
    }

    public static BuildResult failure(String command, int exitCode, String output, String errorSummary) {
        return new BuildResult(false, command, exitCode, output, errorSummary);
    }

    public String abbreviatedOutput(int maxLength) {
        if (output.isBlank()) {
            return "无输出";
        }
        if (maxLength <= 0 || output.length() <= maxLength) {
            return output;
        }
        return output.substring(output.length() - maxLength);
    }
}
