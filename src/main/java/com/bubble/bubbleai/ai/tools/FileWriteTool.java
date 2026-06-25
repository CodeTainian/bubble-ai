package com.bubble.bubbleai.ai.tools;


import com.bubble.bubbleai.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持Ai 通过工具调用的方式写入文件
 */

@Slf4j
public class FileWriteTool {

    @Tool("写入文件到指定路径")
    public String writeFile(@P("文件相对路径")String relativeFilePath,
                            @P("要写入文件的内容")String content,
                            @ToolMemoryId Long appid){
        try {
            Path relativePath = Paths.get(relativeFilePath);
            if (relativePath.isAbsolute()) {
                return "文件写入失败: 不允许使用绝对路径";
            }
            String projectDirName = "react_project_"+appid;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName)
                    .toAbsolutePath()
                    .normalize();
            Path path = projectRoot.resolve(relativePath).normalize();
            if (!path.startsWith(projectRoot)) {
                return "文件写入失败: 文件路径超出项目目录";
            }
            //创建父目录，如果不存在
            Path parentDir = path.getParent();
            if (parentDir!=null){
                Files.createDirectories(parentDir);
            }
            //写入文件内容
            Files.write(path,content.getBytes(), StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件:{}",path.toAbsolutePath());
            //注意要返回相对路径，不能让AI把绝对路径返回给用户
            return "文件写入成功: "+relativeFilePath;
        }catch (Exception e){
            String errorMessage = "文件写入失败: "+relativeFilePath;
            log.error(errorMessage,e);
            return errorMessage;
        }
    }

}
