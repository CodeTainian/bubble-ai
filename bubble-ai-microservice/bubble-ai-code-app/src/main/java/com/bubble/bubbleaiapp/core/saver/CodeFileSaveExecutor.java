package com.bubble.bubbleaiapp.core.saver;

import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;

import java.io.File;

/**
 * 代码文件保存执行器
 * 根据代码生成类型执行相应的保存逻辑
 */
public class CodeFileSaveExecutor {
    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();
    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    /**
     * 执行代码保存
     * @param codeContent 代码结果对象
     * @param codeGenTypeEnum 代码生成类型
     * @return 保存目录
     */
    public static File executeSaver(Object codeContent, CodeGenTypeEnum codeGenTypeEnum,Long appId) throws BusinessException {
        return switch (codeGenTypeEnum){
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeContent,appId);
            case MULTI_FIlE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeContent,appId);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码生成类型："+codeGenTypeEnum);
        };
    }
}
