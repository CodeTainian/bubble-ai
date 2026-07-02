package com.bubble.bubbleai.ai.tools;



import cn.hutool.json.JSONObject;
import com.bubble.bubbleai.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件修改工具，支持AI通过工具调用的方式修改文件内容
 * A file modification tool that supports modifying file content via AI tool calls.
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool{

    @Tool("修改文件内容，用新内容替换指定旧内容")
    public String modifyFile(@P("文件的相对路径") String relativeFilePath,
                             @P("要替换的旧内容") String oldContent,
                             @P("替换后的新内容") String newContent,
                             @ToolMemoryId Long appId) {
        try {
            Path relativePath = Paths.get(relativeFilePath);
            if (relativePath.isAbsolute()) {
                return "文件修改失败: 不允许使用绝对路径";
            }
            String projectDirName = "react_project_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName)
                    .toAbsolutePath()
                    .normalize();
            Path path = projectRoot.resolve(relativePath).normalize();
            if (!path.startsWith(projectRoot)) {
                return "文件修改失败: 文件路径超出项目目录";
            }
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误: 文件不存在或不是文件 - " + relativeFilePath;
            }
            String originalContent = Files.readString(path);
            if (!originalContent.contains(oldContent)) {
                return "警告: 文件中未找到要替换的内容，文件未修改 - " + relativeFilePath;
            }
            String modifiedContent = originalContent.replace(oldContent, newContent);
            if (originalContent.equals(modifiedContent)) {
                return "信息: 替换后文件内容未发生变化 - " + relativeFilePath;
            }
            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("文件修改成功: {}", path.toAbsolutePath());
            return "文件修改成功" + relativeFilePath;
        } catch (Exception e) {
            String errorMessage = "文件修改失败:" + relativeFilePath + ",错误" + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject argument) {
        String relativeFilePath = argument.getStr("relativeFilePath");
        String oldContent = argument.getStr("oldContent");
        String newContent = argument.getStr("newContent");
        return String.format("""
                [工具调用] %s %s
                
                替换前：
                ```
                %s
                ```
                
                替换后：
                ```
                %s
                ```
                """,getDisplayName(),relativeFilePath,oldContent,newContent);
    }
}
