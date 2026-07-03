package com.bubble.bubbleaiapp.core.parser;

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
            // 如果没有提取到 HTML，则提取AI描述
            String description = CodeBlockExtractor.extractSummaryAfterLastCodeBlock(codeContent);
            if (description != null && !description.trim().isEmpty()) {
                htmlCodeResult.setDescription(description.trim());
            }
        }
        return htmlCodeResult;
    }
}
