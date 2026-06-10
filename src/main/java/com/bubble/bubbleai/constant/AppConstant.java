package com.bubble.bubbleai.constant;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;

public interface AppConstant {
    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;
    /**
     * 默认应用的优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * React 工程文件生成目录前缀
     */
    String REACT_PROJECT_FILE_DIR_PREFIX = "react_project_file";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost:8080";

    static String buildCodeOutputDirName(CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        String codeGenType = codeGenTypeEnum == null ? null : codeGenTypeEnum.getValue();
        return buildCodeOutputDirName(codeGenType, appId);
    }

    static String buildCodeOutputDirName(String codeGenType, Long appId) {
        String dirPrefix = CodeGenTypeEnum.REACT_PROJECT.getValue().equals(codeGenType)
                ? REACT_PROJECT_FILE_DIR_PREFIX
                : codeGenType;
        if (dirPrefix == null || dirPrefix.isBlank()) {
            dirPrefix = CodeGenTypeEnum.HTML.getValue();
        }
        return dirPrefix + "_" + appId;
    }

}
