package com.bubble.bubbleaiapp.core.parser;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;

/**
 * 代码解析执行器
 * 根据代码生成类型执行相应的解析逻辑
 */
public class CodeParserExecutor {

    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();
    private static final MutiFileCodeParser mutiFileCodeParser = new MutiFileCodeParser();

    /**
     * 执行代码解析器
     * @param codeContent 代码内容
     * @param codeGenTypeEnum 传入的代码类型
     * @return 解析后结果(HtmlCodeResult,MultiFileCodeResult)
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenTypeEnum){
       return switch (codeGenTypeEnum){
           case HTML -> htmlCodeParser.parserCode(codeContent);
           case MULTI_FIlE ->  mutiFileCodeParser.parserCode(codeContent);
           default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码类型生成器"+codeGenTypeEnum);
       };

    }
}
