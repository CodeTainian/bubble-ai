package com.bubble.bubbleaiapp.core.saver;

import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;

public class MultiFileCodeFileSaverTemplate extends CodeFileSaveTemplate<MultiFileCodeResult>{
    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FIlE;
    }

    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        //保存 HTML 文件
        writeToFile(baseDirPath,"index.html",result.getHtmlCode());
        //保存 CSS 文件
        writeToFile(baseDirPath,"style.css",result.getCssCode());
        //保存 JavaScript 文件
        writeToFile(baseDirPath,"script.js",result.getJsCode());
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"代码内容不能为空");
        }
    }
}
