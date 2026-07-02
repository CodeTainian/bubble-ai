package com.bubble.bubbleai.core.builder;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bubble.bubbleai.ai.AiCodeGeneratorService;
import com.bubble.bubbleai.ai.AiCodeGeneratorServiceFactory;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.ai.model.message.ToolExecutedMessage;
import com.bubble.bubbleai.ai.tools.BaseTool;
import com.bubble.bubbleai.ai.tools.ToolManager;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.service.ChatHistoryService;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs a bounded AI repair loop after a generated React project fails to build.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactProjectBuildRepairService {

    private static final int MAX_REPAIR_ATTEMPTS = 2;
    private static final int REPAIR_TIMEOUT_SECONDS = 240;
    private static final int MAX_BUILD_OUTPUT_LENGTH = 8000;
    private static final int MAX_FILE_SNIPPET_LENGTH = 5000;
    private static final int MAX_HISTORY_MESSAGE_LENGTH = 6000;
    private static final int MAX_RELATED_FILE_COUNT = 8;
    private static final Pattern RELATIVE_FILE_PATTERN = Pattern.compile(
            "(?<![\\w./-])((?:src|public)/[\\w./-]+\\.(?:jsx|js|css|json|html|svg|mjs|cjs))"
    );
    private static final Pattern ROOT_FILE_PATTERN = Pattern.compile(
            "(?<![\\w./-])((?:package\\.json|vite\\.config\\.(?:js|mjs)|index\\.html))(?![\\w./-])"
    );
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "from\\s+['\"]([^'\"]+)['\"]|import\\s+['\"]([^'\"]+)['\"]"
    );

    private final AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;
    private final ChatHistoryService chatHistoryService;
    private final ReactProjectBuilder reactProjectBuilder;
    private final ToolManager toolManager;

    public CompletableFuture<BuildResult> repairAndBuildAsync(long appId, User loginUser, BuildResult initialBuildResult) {
        CompletableFuture<BuildResult> repairFuture = new CompletableFuture<>();
        Thread.ofVirtual().name("react-build-repair-" + appId + "-" + System.currentTimeMillis()).start(() -> {
            try {
                repairFuture.complete(repairAndBuild(appId, loginUser, initialBuildResult));
            } catch (Exception e) {
                log.error("React project auto repair failed, appId={}", appId, e);
                saveHistory(
                        appId,
                        loginUser,
                        ChatHistoryMessageTypeEnum.ERROR,
                        "React 项目自动修复异常：" + e.getMessage()
                );
                repairFuture.complete(BuildResult.failure(
                        "repair react project",
                        -1,
                        initialBuildResult == null ? "" : initialBuildResult.output(),
                        "React 项目自动修复异常：" + e.getMessage()
                ));
            }
        });
        return repairFuture;
    }

    private BuildResult repairAndBuild(long appId, User loginUser, BuildResult initialBuildResult) throws Exception {
        BuildResult currentResult = initialBuildResult;
        String projectPath = buildProjectPath(appId);
        for (int attempt = 1; attempt <= MAX_REPAIR_ATTEMPTS; attempt++) {
            saveHistory(
                    appId,
                    loginUser,
                    ChatHistoryMessageTypeEnum.AI,
                    "检测到 React 项目构建失败，正在进行第 " + attempt + " 次自动修复。"
            );
            String repairPrompt = buildRepairPrompt(projectPath, currentResult, attempt);
            String repairLog = runRepairAi(appId, repairPrompt);
            saveHistory(
                    appId,
                    loginUser,
                    ChatHistoryMessageTypeEnum.AI,
                    formatRepairLog(attempt, repairLog)
            );

            currentResult = reactProjectBuilder.buildProjectWithResult(projectPath);
            if (currentResult.success()) {
                saveHistory(
                        appId,
                        loginUser,
                        ChatHistoryMessageTypeEnum.AI,
                        "React 项目自动修复成功，第 " + attempt + " 次修复后已通过 npm run build。"
                );
                return currentResult;
            }

            saveHistory(
                    appId,
                    loginUser,
                    ChatHistoryMessageTypeEnum.ERROR,
                    "第 " + attempt + " 次自动修复后 React 项目仍构建失败。\n\n" + formatBuildResult(currentResult, 3500)
            );
        }
        return currentResult;
    }

    private String runRepairAi(long appId, String repairPrompt) throws Exception {
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(
                appId,
                CodeGenTypeEnum.REACT_PROJECT
        );
        TokenStream tokenStream = aiCodeGeneratorService.repairReactProjectCodeStream(appId, repairPrompt);
        CompletableFuture<String> completion = new CompletableFuture<>();
        StringBuilder repairLog = new StringBuilder();
        tokenStream
                .onPartialResponse(repairLog::append)
                .onToolExecuted(toolExecution -> repairLog.append(formatToolExecuted(toolExecution)))
                .onCompleteResponse(response -> completion.complete(repairLog.toString()))
                .onError(completion::completeExceptionally)
                .start();
        return completion.get(REPAIR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String buildRepairPrompt(String projectPath, BuildResult buildResult, int attempt) {
        String sanitizedOutput = sanitizeBuildOutput(buildResult == null ? "" : buildResult.abbreviatedOutput(MAX_BUILD_OUTPUT_LENGTH), projectPath);
        String relatedFiles = collectRelatedFileSnippets(projectPath, buildResult == null ? "" : buildResult.output());
        return String.format("""
                当前 React + Vite 项目构建失败，请修复现有项目文件。项目根目录由工具自动定位，请所有工具参数都使用相对路径。

                修复次数：第 %d 次，最多 %d 次。

                构建命令：
                %s

                退出码：
                %d

                错误摘要：
                %s

                构建输出：
                %s

                已提取的相关文件内容：
                %s

                修复要求：
                - 优先修复构建错误直接指向的问题。
                - 不要重写整个项目。
                - 不要输出完整代码块。
                - 修复完成后用一句话说明改动。
                """,
                attempt,
                MAX_REPAIR_ATTEMPTS,
                buildResult == null ? "" : buildResult.command(),
                buildResult == null ? -1 : buildResult.exitCode(),
                buildResult == null ? "" : buildResult.errorSummary(),
                sanitizedOutput,
                relatedFiles
        );
    }

    private String collectRelatedFileSnippets(String projectPath, String buildOutput) {
        Path projectRoot = Path.of(projectPath).toAbsolutePath().normalize();
        Set<String> relatedFiles = extractRelatedFilePaths(buildOutput);
        addImportTargets(projectRoot, relatedFiles);
        if (relatedFiles.isEmpty()) {
            return "未从构建日志中提取到具体文件，请先调用读取目录工具定位相关文件。";
        }
        StringBuilder snippets = new StringBuilder();
        int count = 0;
        for (String relativeFile : relatedFiles) {
            if (count >= MAX_RELATED_FILE_COUNT) {
                break;
            }
            Path filePath = projectRoot.resolve(relativeFile).normalize();
            if (!filePath.startsWith(projectRoot) || !Files.isRegularFile(filePath)) {
                continue;
            }
            try {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                snippets.append("\n--- ").append(relativeFile).append(" ---\n")
                        .append(abbreviate(content, MAX_FILE_SNIPPET_LENGTH))
                        .append("\n");
                count++;
            } catch (Exception e) {
                log.warn("read related file failed, file={}", relativeFile, e);
            }
        }
        return snippets.isEmpty() ? "相关文件当前不可读取，请通过工具读取目录和文件后修复。" : snippets.toString();
    }

    private Set<String> extractRelatedFilePaths(String buildOutput) {
        Set<String> paths = new LinkedHashSet<>();
        collectMatches(paths, RELATIVE_FILE_PATTERN, buildOutput);
        collectMatches(paths, ROOT_FILE_PATTERN, buildOutput);
        return paths;
    }

    private void collectMatches(Set<String> result, Pattern pattern, String text) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
    }

    private void addImportTargets(Path projectRoot, Set<String> relatedFiles) {
        List<String> sourceFiles = new ArrayList<>(relatedFiles);
        for (String relativeFile : sourceFiles) {
            Path filePath = projectRoot.resolve(relativeFile).normalize();
            if (!filePath.startsWith(projectRoot) || !Files.isRegularFile(filePath)) {
                continue;
            }
            try {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                Matcher matcher = IMPORT_PATTERN.matcher(content);
                while (matcher.find()) {
                    String importPath = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    resolveImportPath(projectRoot, relativeFile, importPath).forEach(relatedFiles::add);
                }
            } catch (Exception e) {
                log.warn("extract import targets failed, file={}", relativeFile, e);
            }
        }
    }

    private List<String> resolveImportPath(Path projectRoot, String sourceRelativeFile, String importPath) {
        if (StrUtil.isBlank(importPath) || !(importPath.startsWith("@/") || importPath.startsWith("./") || importPath.startsWith("../"))) {
            return List.of();
        }
        Path sourceDir = projectRoot.resolve(sourceRelativeFile).normalize().getParent();
        Path basePath = importPath.startsWith("@/")
                ? projectRoot.resolve("src").resolve(importPath.substring(2)).normalize()
                : sourceDir.resolve(importPath).normalize();
        if (!basePath.startsWith(projectRoot)) {
            return List.of();
        }
        List<String> candidates = List.of(
                "",
                ".js",
                ".jsx",
                ".css",
                File.separator + "index.js",
                File.separator + "index.jsx"
        );
        List<String> resolved = new ArrayList<>();
        for (String suffix : candidates) {
            Path candidate = Path.of(basePath.toString() + suffix).normalize();
            if (candidate.startsWith(projectRoot) && Files.isRegularFile(candidate)) {
                resolved.add(toRelativeUnixPath(projectRoot, candidate));
            }
        }
        return resolved;
    }

    private String formatToolExecuted(dev.langchain4j.service.tool.ToolExecution toolExecution) {
        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
        BaseTool tool = toolManager.getTool(toolExecutedMessage.getName());
        if (tool == null) {
            return "\n\n[工具调用] " + toolExecutedMessage.getName() + "\n\n";
        }
        try {
            JSONObject argument = parseToolArgument(toolExecutedMessage.getArgument());
            return "\n\n" + tool.generateToolExecutedResult(argument) + "\n\n";
        } catch (Exception e) {
            log.warn("format repair tool execution failed, toolName={}", toolExecutedMessage.getName(), e);
            return "\n\n[工具调用] " + tool.getDisplayName() + "\n\n";
        }
    }

    private JSONObject parseToolArgument(String argument) {
        if (StrUtil.isBlank(argument)) {
            return new JSONObject();
        }
        JSONObject jsonObject = JSONUtil.parseObj(argument);
        if (!jsonObject.containsKey("relativeFilePath") && jsonObject.containsKey("relativePath")) {
            jsonObject.set("relativeFilePath", jsonObject.get("relativePath"));
        }
        if (!jsonObject.containsKey("relativePath") && jsonObject.containsKey("relativeFilePath")) {
            jsonObject.set("relativePath", jsonObject.get("relativeFilePath"));
        }
        return jsonObject;
    }

    private String formatRepairLog(int attempt, String repairLog) {
        String normalizedLog = StrUtil.blankToDefault(repairLog, "AI 已完成自动修复。");
        return "第 " + attempt + " 次 React 项目自动修复记录：\n\n" + abbreviate(normalizedLog, MAX_HISTORY_MESSAGE_LENGTH);
    }

    private String formatBuildResult(BuildResult buildResult, int maxOutputLength) {
        if (buildResult == null) {
            return "无构建结果";
        }
        return String.format("""
                命令：%s
                退出码：%d
                摘要：%s
                输出：
                %s
                """,
                buildResult.command(),
                buildResult.exitCode(),
                buildResult.errorSummary(),
                buildResult.abbreviatedOutput(maxOutputLength)
        );
    }

    private String sanitizeBuildOutput(String output, String projectPath) {
        if (StrUtil.isBlank(output)) {
            return "无输出";
        }
        String projectPrefix = Path.of(projectPath).toAbsolutePath().normalize().toString();
        return output.replace(projectPrefix + File.separator, "");
    }

    private String buildProjectPath(long appId) {
        return AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + CodeGenTypeEnum.REACT_PROJECT.getValue() + "_" + appId;
    }

    private String toRelativeUnixPath(Path projectRoot, Path filePath) {
        return projectRoot.relativize(filePath).toString().replace(File.separatorChar, '/');
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(text.length() - maxLength);
    }

    private void saveHistory(long appId, User loginUser, ChatHistoryMessageTypeEnum messageType, String message) {
        if (loginUser == null || loginUser.getId() == null || StrUtil.isBlank(message)) {
            return;
        }
        try {
            chatHistoryService.addChatMessage(
                    appId,
                    loginUser.getId(),
                    messageType.getValue(),
                    abbreviate(message, MAX_HISTORY_MESSAGE_LENGTH),
                    null
            );
        } catch (Exception e) {
            log.error("save React build repair history failed, appId={}", appId, e);
        }
    }
}
