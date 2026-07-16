package com.bubble.bubbleai.core.builder;

import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.core.handler.GenerationStateService;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.model.enums.ChatMessageSource;
import com.bubble.bubbleai.model.enums.GenerationState;
import com.bubble.bubbleai.monitor.MonitorContext;
import com.bubble.bubbleai.monitor.MonitorContextHolder;
import com.bubble.bubbleai.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Bounded build -> AI repair -> rebuild state machine. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactProjectBuildRepairService {

    private static final int REPEATED_ERROR_STOP_THRESHOLD = 3;
    private static final int MAX_RELATED_FILE_COUNT = 8;
    private static final int MAX_FILE_SNIPPET_LENGTH = 2500;
    private static final int MAX_PROJECT_TREE_LENGTH = 6000;
    private static final Pattern RELATED_FILE_PATTERN = Pattern.compile(
            "(?<![\\w./-])((?:src|public)/[\\w./-]+\\.(?:tsx|ts|jsx|js|css|json|html|svg|mjs|cjs))"
    );
    private static final Pattern ROOT_FILE_PATTERN = Pattern.compile(
            "(?<![\\w./-])((?:package\\.json|vite\\.config\\.(?:ts|js|mjs)|tsconfig[^/\\s]*\\.json|index\\.html))(?![\\w./-])"
    );

    private final ReactProjectBuilder projectBuilder;
    private final ReactProjectRepairAgent repairAgent;
    private final ReactGenerationProperties properties;
    private final BuildLogSanitizer logSanitizer;
    private final GenerationStateService stateService;
    private final ChatHistoryService chatHistoryService;
    private final RedissonClient redissonClient;

    public CompletableFuture<BuildResult> repairAndBuildAsync(
            long appId,
            String generationId,
            String originalUserMessage,
            User loginUser,
            MonitorContext monitorContext,
            BuildResult initialBuildResult,
            Consumer<String> statusConsumer) {
        CompletableFuture<BuildResult> future = new CompletableFuture<>();
        Thread.ofVirtual().name("react-build-repair-" + appId + "-" + generationId).start(() -> {
            try (MonitorContextHolder.Scope ignored = MonitorContextHolder.openScope(repairMonitorContext(monitorContext))) {
                future.complete(repairAndBuild(appId, generationId, originalUserMessage,
                        loginUser, initialBuildResult, statusConsumer));
            } catch (Exception e) {
                log.error("React auto repair failed, appId={}, generationId={}", appId, generationId, e);
                stateService.transition(appId, generationId, GenerationState.FAILED_MAX_ATTEMPTS, 0);
                emit(statusConsumer, "自动修复未成功，请稍后重试");
                future.complete(BuildResult.failure(
                        "auto_repair", "repair react project", -1, "", "",
                        "React 项目自动修复异常", 0, false));
            }
        });
        return future;
    }

    BuildResult repairAndBuild(long appId, String generationId, String originalUserMessage,
                               User loginUser, BuildResult initialBuildResult,
                               Consumer<String> statusConsumer) throws Exception {
        RLock lock = redissonClient.getLock("bubble-ai:generation:react-repair:" + appId);
        long leaseSeconds = calculateLeaseSeconds();
        if (!lock.tryLock(0, leaseSeconds, TimeUnit.SECONDS)) {
            emit(statusConsumer, "已有修复任务正在运行，请稍后查看结果");
            return BuildResult.failure("auto_repair", "acquire repair lock", -4,
                    "", "", "该应用已有自动修复任务", 0, false);
        }
        try {
            return runRepairLoop(appId, generationId, originalUserMessage,
                    loginUser, initialBuildResult, statusConsumer);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private BuildResult runRepairLoop(long appId, String generationId, String originalUserMessage,
                                      User loginUser, BuildResult initialBuildResult,
                                      Consumer<String> statusConsumer) throws Exception {
        BuildResult current = initialBuildResult;
        String projectPath = projectPath(appId);
        String previousRepairSummary = "无";
        String previousHash = current.errorHash();
        int identicalErrorOccurrences = 1;
        boolean repeatedError = false;
        int lastAttempt = 0;
        saveInternalBuildFailure(appId, loginUser, generationId, current, 0);

        for (int attempt = 1; attempt <= properties.getMaxRepairAttempts(); attempt++) {
            lastAttempt = attempt;
            stateService.transition(appId, generationId, GenerationState.AI_REPAIRING, attempt);
            emit(statusConsumer, String.format("发现构建错误，正在尝试自动修复（%d/%d）",
                    attempt, properties.getMaxRepairAttempts()));
            String prompt = buildRepairPrompt(projectPath, originalUserMessage, current,
                    previousRepairSummary, attempt, repeatedError);
            ReactProjectRepairAgent.RepairOutcome outcome = repairAgent.repair(
                    appId, generationId + "-" + attempt, prompt);
            previousRepairSummary = outcome.safeSummary();
            stateService.transition(appId, generationId, GenerationState.REPAIR_COMPLETED, attempt);
            emit(statusConsumer, "代码修复完成，正在重新构建");

            stateService.transition(appId, generationId, GenerationState.BUILDING, attempt);
            BuildResult rebuilt = projectBuilder.buildProjectWithResult(projectPath);
            if (rebuilt.success()) {
                stateService.transition(appId, generationId, GenerationState.SUCCESS, attempt);
                emit(statusConsumer, "项目构建成功");
                return rebuilt;
            }

            stateService.transition(appId, generationId, GenerationState.BUILD_FAILED, attempt);
            saveInternalBuildFailure(appId, loginUser, generationId, rebuilt, attempt);
            String currentHash = rebuilt.errorHash();
            if (currentHash.equals(previousHash)) {
                identicalErrorOccurrences++;
                repeatedError = true;
                log.warn("Repeated React build error, appId={}, generationId={}, attempt={}, hash={}",
                        appId, generationId, attempt, abbreviatedHash(currentHash));
            } else {
                identicalErrorOccurrences = 1;
                repeatedError = false;
            }
            current = rebuilt;
            previousHash = currentHash;
            if (properties.isStopOnRepeatedError()
                    && identicalErrorOccurrences >= REPEATED_ERROR_STOP_THRESHOLD) {
                log.warn("Stopping ineffective React repair loop, appId={}, generationId={}, hash={}",
                        appId, generationId, abbreviatedHash(currentHash));
                break;
            }
        }

        stateService.transition(appId, generationId, GenerationState.FAILED_MAX_ATTEMPTS,
                lastAttempt);
        emit(statusConsumer, "自动修复未成功，请稍后重试");
        return current;
    }

    private String buildRepairPrompt(String projectPath, String originalUserMessage,
                                     BuildResult result, String previousRepairSummary,
                                     int attempt, boolean repeatedError) {
        String safeStdout = logSanitizer.sanitize(result.stdout(), projectPath,
                properties.getMaxBuildLogLength() / 2);
        String safeStderr = logSanitizer.sanitize(result.stderr(), projectPath,
                properties.getMaxBuildLogLength() / 2);
        String relatedFiles = relatedFileSnippets(projectPath, result.output());
        String repeatedWarning = repeatedError
                ? "上一次修改后构建错误 hash 未变化，说明上次修改无效。请重新检查根因，不要重复相同修改。"
                : "无";
        return String.format("""
                当前 React + Vite 项目构建失败。这是自动修复流程的第 %d/%d 次。

                原始用户需求：
                %s

                构建阶段：%s
                构建命令：%s
                退出码：%d
                是否超时：%s
                耗时毫秒：%d
                错误摘要：%s

                stdout（已脱敏并截断）：
                %s

                stderr（已脱敏并截断）：
                %s

                项目文件结构：
                %s

                可能相关的文件内容（已脱敏并截断）：
                %s

                上一次修复结果：%s
                重复错误提示：%s

                请检查当前工程并直接调用文件工具进行局部修改。
                要求：
                1. 必须真正修改文件，不要只解释；
                2. 不要删除用户要求的核心功能、页面或路由来规避错误；
                3. 禁止用 any、注释代码、删除页面或关闭类型检查来绕过构建错误；
                4. 重点检查 import、类型、导出、路由、依赖和 Vite 配置；
                5. 工具路径必须是项目内相对路径；
                6. 修改完成后停止，由后端重新构建验证。
                """,
                attempt, properties.getMaxRepairAttempts(), StrUtil.blankToDefault(originalUserMessage, "未提供"),
                result.stage(), result.command(), result.exitCode(), result.timedOut(), result.durationMillis(),
                result.summary(), safeStdout, safeStderr, projectTree(projectPath), relatedFiles,
                previousRepairSummary, repeatedWarning);
    }

    private String projectTree(String projectPath) {
        Path root = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return "项目目录不可读取";
        }
        StringBuilder tree = new StringBuilder();
        try (Stream<Path> paths = Files.walk(root, 4)) {
            paths.filter(path -> !path.equals(root))
                    .filter(path -> !containsIgnoredDirectory(root.relativize(path)))
                    .sorted()
                    .limit(250)
                    .forEach(path -> {
                        if (tree.length() < MAX_PROJECT_TREE_LENGTH) {
                            tree.append(root.relativize(path).toString().replace(File.separatorChar, '/'));
                            if (Files.isDirectory(path)) {
                                tree.append('/');
                            }
                            tree.append('\n');
                        }
                    });
        } catch (Exception e) {
            return "项目目录读取失败";
        }
        return tree.isEmpty() ? "项目目录为空" : tree.toString();
    }

    private boolean containsIgnoredDirectory(Path relativePath) {
        for (Path part : relativePath) {
            String value = part.toString();
            if (value.equals("node_modules") || value.equals("dist") || value.equals(".git")) {
                return true;
            }
        }
        return false;
    }

    private String relatedFileSnippets(String projectPath, String output) {
        Path root = Path.of(projectPath).toAbsolutePath().normalize();
        Set<String> related = new LinkedHashSet<>();
        collectMatches(related, RELATED_FILE_PATTERN, output);
        collectMatches(related, ROOT_FILE_PATTERN, output);
        if (related.isEmpty()) {
            return "构建日志未指向具体文件，请先使用目录和文件读取工具定位。";
        }
        StringBuilder snippets = new StringBuilder();
        int count = 0;
        for (String relativeFile : related) {
            if (count >= MAX_RELATED_FILE_COUNT) {
                break;
            }
            Path file = root.resolve(relativeFile).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String safeContent = logSanitizer.sanitize(content, projectPath, MAX_FILE_SNIPPET_LENGTH);
                snippets.append("\n--- ").append(relativeFile).append(" ---\n")
                        .append(safeContent).append('\n');
                count++;
            } catch (Exception e) {
                log.debug("Unable to read related build file, file={}", relativeFile, e);
            }
        }
        return snippets.isEmpty() ? "相关文件不可读取，请通过文件工具定位。" : snippets.toString();
    }

    private void collectMatches(Set<String> target, Pattern pattern, String text) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            target.add(matcher.group(1));
        }
    }

    private void saveInternalBuildFailure(long appId, User user, String generationId,
                                          BuildResult result, int attempt) {
        if (user == null || user.getId() == null) {
            return;
        }
        try {
            String projectPath = projectPath(appId);
            String internalMessage = String.format("""
                    generationId=%s
                    repairAttempt=%d
                    stage=%s
                    command=%s
                    exitCode=%d
                    timedOut=%s
                    durationMillis=%d
                    errorHash=%s
                    summary=%s
                    output=%s
                    """,
                    generationId, attempt, result.stage(), result.command(), result.exitCode(), result.timedOut(),
                    result.durationMillis(), result.errorHash(), result.summary(),
                    logSanitizer.sanitize(result.output(), projectPath, properties.getMaxBuildLogLength()));
            chatHistoryService.addInternalMessage(appId, user.getId(),
                    ChatHistoryMessageTypeEnum.ERROR.getValue(), internalMessage,
                    attempt == 0 ? ChatMessageSource.BUILD_ERROR : ChatMessageSource.AUTO_REPAIR);
        } catch (Exception e) {
            log.warn("Unable to persist internal build diagnostics, appId={}, generationId={}",
                    appId, generationId, e);
        }
    }

    private MonitorContext repairMonitorContext(MonitorContext parent) {
        MonitorContext context = parent == null ? MonitorContext.system() : parent.snapshot();
        context.setActorType("AUTO_REPAIR");
        return context;
    }

    private String projectPath(long appId) {
        return AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator
                + CodeGenTypeEnum.REACT_PROJECT.getValue() + "_" + appId;
    }

    private long calculateLeaseSeconds() {
        long perAttempt = (long) properties.getInstallTimeoutSeconds()
                + properties.getBuildTimeoutSeconds()
                + properties.getRepairAiTimeoutSeconds();
        return Math.max(60, perAttempt * properties.getMaxRepairAttempts() + 60);
    }

    private void emit(Consumer<String> consumer, String status) {
        if (consumer != null) {
            consumer.accept("\n\n" + status + "\n\n");
        }
    }

    private String abbreviatedHash(String hash) {
        return hash == null || hash.length() <= 12 ? hash : hash.substring(0, 12);
    }
}
