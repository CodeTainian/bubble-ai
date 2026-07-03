package com.bubble.bubbleaiapp.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ReactProjectBuilder {

    /**
     * 异步构建项目（不阻塞主流程）
     * @param projectPath 项目路径
     * @return 构建结果 Future
     */
    public CompletableFuture<Boolean> buildProjectAsync(String projectPath){
        return buildProjectWithResultAsync(projectPath).thenApply(BuildResult::success);
    }

    /**
     * 异步构建项目并返回完整构建结果。
     *
     * @param projectPath 项目路径
     * @return 构建结果 Future
     */
    public CompletableFuture<BuildResult> buildProjectWithResultAsync(String projectPath){
        CompletableFuture<BuildResult> buildFuture = new CompletableFuture<>();
        Thread.ofVirtual().name("react-builder-"+System.currentTimeMillis()).start(()->{
            try {
                buildFuture.complete(buildProjectWithResult(projectPath));
            }catch (Exception e){
                log.error("异步构建React项目时发生异常: {}",e.getMessage(),e);
                buildFuture.complete(BuildResult.failure(
                        "build react project",
                        -1,
                        "",
                        "异步构建 React 项目时发生异常: " + e.getMessage()
                ));
            }
        });
        return buildFuture;
    }

    /**
     * 构建 React 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        return buildProjectWithResult(projectPath).success();
    }

    /**
     * 构建 React 项目，并返回可用于诊断和自动修复的结构化结果。
     *
     * @param projectPath 项目根目录路径
     * @return 构建结果
     */
    public BuildResult buildProjectWithResult(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return BuildResult.failure("validate project directory", -1, "", "项目目录不存在: " + projectPath);
        }
        // 检查 package.json 是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return BuildResult.failure("validate package.json", -1, "", "package.json 文件不存在: " + packageJson.getAbsolutePath());
        }
        if (!ensureViteEntryScript(projectDir)) {
            return BuildResult.failure("validate vite entry", -1, "", "检查或修复 Vite 入口脚本失败");
        }
        log.info("开始构建 React 项目: {}", projectPath);
        // 执行 npm install
        BuildResult installResult = executeNpmInstall(projectDir);
        if (!installResult.success()) {
            log.error("npm install 执行失败");
            return installResult;
        }
        // 执行 npm run build
        BuildResult buildResult = executeNpmBuild(projectDir);
        if (!buildResult.success()) {
            log.error("npm run build 执行失败");
            return buildResult;
        }
        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return BuildResult.failure(
                    "validate dist directory",
                    -1,
                    buildResult.output(),
                    "构建完成但 dist 目录未生成: " + distDir.getAbsolutePath()
            );
        }
        log.info("React 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return buildResult;
    }

    /**
     * Vite 的入口脚本必须写在 index.html 中。若模型漏写，build 会成功但 dist/index.html 只有空 root。
     */
    private boolean ensureViteEntryScript(File projectDir) {
        File indexHtml = new File(projectDir, "index.html");
        if (!indexHtml.exists()) {
            log.error("index.html 文件不存在: {}", indexHtml.getAbsolutePath());
            return false;
        }
        File mainJsx = new File(projectDir, "src/main.jsx");
        if (!mainJsx.exists()) {
            log.error("src/main.jsx 文件不存在: {}", mainJsx.getAbsolutePath());
            return false;
        }
        try {
            Path indexPath = indexHtml.toPath();
            String html = Files.readString(indexPath, StandardCharsets.UTF_8);
            if (html.contains("src/main.jsx")) {
                return true;
            }
            String entryScript = "    <script type=\"module\" src=\"/src/main.jsx\"></script>\n";
            String updatedHtml = html.contains("</body>")
                    ? html.replace("</body>", entryScript + "  </body>")
                    : html + "\n" + entryScript;
            Files.writeString(indexPath, updatedHtml, StandardCharsets.UTF_8);
            log.warn("index.html 缺少 Vite 入口脚本，已自动补充: {}", indexHtml.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("检查或修复 index.html 入口脚本失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行 npm install 命令
     */
    private BuildResult executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private BuildResult executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180); // 3分钟超时
    }


    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }


    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 命令执行结果
     */
    private BuildResult executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            StringBuilder output = new StringBuilder();
            Process process = new ProcessBuilder(command.split("\\s+"))
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start();
            Thread outputReader = Thread.ofVirtual().name("react-build-output-" + System.currentTimeMillis()).start(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append(System.lineSeparator());
                    }
                } catch (Exception e) {
                    log.warn("读取命令输出失败: {}", e.getMessage());
                }
            });
            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                outputReader.join(1000);
                log.error("命令超时前输出: {}", abbreviateOutput(output.toString()));
                return BuildResult.failure(command, -2, output.toString(), "命令执行超时（" + timeoutSeconds + "秒）");
            }
            outputReader.join(1000);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return BuildResult.success(command, output.toString());
            } else {
                log.error("命令执行失败，退出码: {}", exitCode);
                log.error("命令输出: {}", abbreviateOutput(output.toString()));
                return BuildResult.failure(command, exitCode, output.toString(), "命令执行失败，退出码: " + exitCode);
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return BuildResult.failure(command, -1, "", "执行命令失败: " + e.getMessage());
        }
    }

    private String abbreviateOutput(String output) {
        if (output == null || output.isBlank()) {
            return "无输出";
        }
        int maxLength = 6000;
        return output.length() <= maxLength ? output : output.substring(output.length() - maxLength);
    }

}
