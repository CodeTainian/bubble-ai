package com.bubble.bubbleai.ai.model.tools;


import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件写入工具
 * 支持Ai 通过工具调用的方式写入文件
 */

@Slf4j
public class FileWriteTool {

    private final Path projectRoot;

    public FileWriteTool(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    @Tool("写入或更新 React 项目中的单个文件。每次只能写入一个文件。")
    public String writeFile(@P("文件相对路径，例如 package.json、index.html、src/main.jsx、src/App.jsx、src/pages/Index.jsx")String relativeFilePath,
                            @P("完整的文件内容")String content,
                            @P("本次文件变更的 8 到 24 字中文短总结，例如：创建商品卡片组件") String description,
                            @ToolMemoryId Long appid){
        try {
            Path targetPath = projectRoot.resolve(relativeFilePath).normalize();
            if (!targetPath.startsWith(projectRoot)) {
                return "写入失败：非法文件路径 " + relativeFilePath;
            }
//            Path path = Paths.get(relativeFilePath);
//            if (!path.isAbsolute()){
//                String projectDirName = "react_project_"+appid;
//                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
//                path = projectRoot.resolve(relativeFilePath);
//            }
            //创建父目录，如果不存在
            Path parentDir = targetPath.getParent();
            if (parentDir!=null){
                Files.createDirectories(parentDir);
            }
            //写入文件内容
            Files.writeString(targetPath,content,
                    StandardCharsets.UTF_8);
            log.info("成功写入文件:{}",targetPath.toAbsolutePath());
            //注意要返回相对路径，不能让AI把绝对路径返回给用户
            return "文件写入成功: "+relativeFilePath;
        }catch (Exception e){
            log.error("写入文件失败：{}", relativeFilePath, e);
            return "写入失败：" + relativeFilePath + "，原因：" + e.getMessage();
        }
    }

}
