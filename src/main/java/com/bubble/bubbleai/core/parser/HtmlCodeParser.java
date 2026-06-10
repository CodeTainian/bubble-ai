package com.bubble.bubbleai.core.parser;

import com.bubble.bubbleai.ai.model.HtmlCodeResult;

public class HtmlCodeParser implements CodeParser<HtmlCodeResult>{

    /**
     * 解析html单文件代码
     * @param codeContent 原始内容
     * @return html代码
     */
    @Override
    public HtmlCodeResult parserCode(String codeContent) {
        HtmlCodeResult htmlCodeResult = new HtmlCodeResult();
        //提取html代码
        String extractedHtmlCode = CodeBlockExtractor.extractHtmlCode(codeContent);
        if (extractedHtmlCode != null&&!extractedHtmlCode.trim().isEmpty()) {
            htmlCodeResult.setHtmlCode(extractedHtmlCode);
        }else {
            //如果没有提取到代码块，则将整个内容作为html
            htmlCodeResult.setHtmlCode(codeContent);
        }
        return htmlCodeResult;
    }
}
