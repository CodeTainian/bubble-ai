package com.bubble.bubbleai.core.builder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactProjectBuilder {

    private final ReactGenerationProperties properties;
    private final BuildLogSanitizer buildLogSanitizer;

    public CompletableFuture<Boolean> buildProjectAsync(String projectPath) {
        return buildProjectWithResultAsync(projectPath).thenApply(BuildResult::success);
    }

    public CompletableFuture<BuildResult> buildProjectWithResultAsync(String projectPath) {
        CompletableFuture<BuildResult> future = new CompletableFuture<>();
        Thread.ofVirtual().name("react-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                future.complete(buildProjectWithResult(projectPath));
            } catch (Exception e) {
                log.error("React build task failed", e);
                future.complete(BuildResult.failure(
                        "build", "build react project", -1, "", "",
                        "React 项目构建任务异常", 0, false));
            }
        });
        return future;
    }

    public boolean buildProject(String projectPath) {
        return buildProjectWithResult(projectPath).success();
    }

    public BuildResult buildProjectWithResult(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.isDirectory()) {
            return validationFailure("validate project directory", "项目目录不存在");
        }
        if (!new File(projectDir, "package.json").isFile()) {
            return validationFailure("validate package.json", "package.json 文件不存在");
        }
        if (!ensureViteEntryScript(projectDir)) {
            return validationFailure("validate vite entry", "检查或修复 Vite 入口脚本失败");
        }

        BuildResult installResult = executeCommand(
                projectDir,
                "npm_install",
                List.of(npmCommand(), "install"),
                properties.getInstallTimeoutSeconds());
        if (!installResult.success()) {
            return installResult;
        }

        BuildResult buildResult = executeCommand(
                projectDir,
                "npm_build",
                List.of(npmCommand(), "run", "build"),
                properties.getBuildTimeoutSeconds());
        if (!buildResult.success()) {
            return buildResult;
        }

        if (!new File(projectDir, "dist").isDirectory()) {
            return BuildResult.failure(
                    "validate_dist", "validate dist directory", -1,
                    buildResult.stdout(), buildResult.stderr(),
                    "构建命令成功，但未生成 dist 目录", buildResult.durationMillis(), false);
        }
        return buildResult;
    }

    private BuildResult validationFailure(String command, String summary) {
        return BuildResult.failure("validation", command, -1, "", "", summary, 0, false);
    }

    /** Vite needs an explicit module entry even if the generated HTML omitted it. */
    private boolean ensureViteEntryScript(File projectDir) {
        File indexHtml = new File(projectDir, "index.html");
        File mainJsx = new File(projectDir, "src/main.jsx");
        if (!indexHtml.isFile() || !mainJsx.isFile()) {
            return false;
        }
        try {
            Path indexPath = indexHtml.toPath();
            String html = Files.readString(indexPath, StandardCharsets.UTF_8);
            if (html.contains("src/main.jsx")) {
                return true;
            }
            String entryScript = "    <script type=\"module\" src=\"/src/main.jsx\"></script>\n";
            String updated = html.contains("</body>")
                    ? html.replace("</body>", entryScript + "  </body>")
                    : html + "\n" + entryScript;
            Files.writeString(indexPath, updated, StandardCharsets.UTF_8);
            log.warn("Vite entry was missing and has been restored, project={}", projectDir.getName());
            return true;
        } catch (Exception e) {
            log.error("Failed to validate Vite entry, project={}", projectDir.getName(), e);
            return false;
        }
    }

    private BuildResult executeCommand(File workingDir, String stage,
                                       List<String> commandParts, int timeoutSeconds) {
        long startedAt = System.nanoTime();
        String command = String.join(" ", commandParts);
        int perStreamLimit = Math.max(2000, properties.getMaxBuildLogLength() / 2);
        BoundedLogBuffer stdout = new BoundedLogBuffer(perStreamLimit);
        BoundedLogBuffer stderr = new BoundedLogBuffer(perStreamLimit);
        Process process = null;
        try {
            process = new ProcessBuilder(commandParts)
                    .directory(workingDir)
                    .start();
            Process runningProcess = process;
            Thread stdoutReader = Thread.ofVirtual().name("react-build-stdout").start(
                    () -> readStream(runningProcess.getInputStream(), stdout));
            Thread stderrReader = Thread.ofVirtual().name("react-build-stderr").start(
                    () -> readStream(runningProcess.getErrorStream(), stderr));

            boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                destroyProcessTree(process);
            }
            stdoutReader.join(2000);
            stderrReader.join(2000);
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            if (!finished) {
                BuildResult result = BuildResult.failure(
                        stage, command, -2, stdout.value(), stderr.value(),
                        command + " 执行超时（" + timeoutSeconds + " 秒）",
                        durationMillis, true);
                logFailure(workingDir, result);
                return result;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return BuildResult.success(stage, command, stdout.value(), stderr.value(), durationMillis);
            }
            BuildResult result = BuildResult.failure(
                    stage, command, exitCode, stdout.value(), stderr.value(),
                    command + " 执行失败，退出码 " + exitCode,
                    durationMillis, false);
            logFailure(workingDir, result);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                destroyProcessTree(process);
            }
            return BuildResult.failure(stage, command, -3, stdout.value(), stderr.value(),
                    "构建任务被取消", elapsedMillis(startedAt), false);
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                destroyProcessTree(process);
            }
            log.error("Failed to execute React build command, stage={}", stage, e);
            return BuildResult.failure(stage, command, -1, stdout.value(), stderr.value(),
                    "无法执行构建命令", elapsedMillis(startedAt), false);
        }
    }

    private void readStream(InputStream stream, BoundedLogBuffer target) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                target.append(line + System.lineSeparator());
            }
        } catch (Exception e) {
            target.append("[读取命令输出失败: " + e.getClass().getSimpleName() + "]\n");
        }
    }

    private void destroyProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logFailure(File workingDir, BuildResult result) {
        String safeOutput = buildLogSanitizer.sanitize(
                result.abbreviatedOutput(6000), workingDir.getAbsolutePath(), 6000);
        log.warn("React build failed, project={}, stage={}, exitCode={}, timedOut={}, durationMs={}, output={}",
                workingDir.getName(), result.stage(), result.exitCode(), result.timedOut(),
                result.durationMillis(), safeOutput);
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String npmCommand() {
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "npm.cmd" : "npm";
    }

    private static final class BoundedLogBuffer {
        private final int maxLength;
        private final int headLimit;
        private final int tailLimit;
        private final StringBuilder head = new StringBuilder();
        private final ArrayDeque<String> tail = new ArrayDeque<>();
        private int tailLength;
        private long totalLength;

        private BoundedLogBuffer(int maxLength) {
            this.maxLength = maxLength;
            this.headLimit = Math.min(2000, Math.max(500, maxLength / 4));
            this.tailLimit = Math.max(500, maxLength - headLimit);
        }

        private synchronized void append(String value) {
            totalLength += value.length();
            if (head.length() < headLimit) {
                int remaining = headLimit - head.length();
                head.append(value, 0, Math.min(remaining, value.length()));
            }
            tail.addLast(value);
            tailLength += value.length();
            while (tailLength > tailLimit && tail.size() > 1) {
                tailLength -= tail.removeFirst().length();
            }
        }

        private synchronized String value() {
            if (totalLength <= headLimit) {
                return head.toString();
            }
            StringBuilder result = new StringBuilder(maxLength + 64)
                    .append(head)
                    .append("\n... 日志已截断，保留末尾 ...\n");
            tail.forEach(result::append);
            if (result.length() > maxLength) {
                return result.substring(0, headLimit)
                        + "\n... 日志已截断，保留末尾 ...\n"
                        + result.substring(result.length() - tailLimit);
            }
            return result.toString();
        }
    }
}
