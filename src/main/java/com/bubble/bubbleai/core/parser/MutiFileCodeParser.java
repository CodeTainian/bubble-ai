package com.bubble.bubbleai.core.parser;

import com.bubble.bubbleai.ai.model.MultiFileCodeResult;

public class MutiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    /**
     * 提取多个文件
     * @param codeContent;
     * @return 提取后的文件内容
     */
    @Override
    public MultiFileCodeResult parserCode(String codeContent) {
        MultiFileCodeResult multiFileCodeResult = new MultiFileCodeResult();
        //html 代码
        String htmlCode = CodeBlockExtractor.extractHtmlCode(codeContent);
        //css 代码
        String cssCode = CodeBlockExtractor.extractCssCode(codeContent);
        //js 代码
        String jsCode = CodeBlockExtractor.extractJsCode(codeContent);
        //设置html代码
        if (htmlCode != null&&!htmlCode.trim().isEmpty()) {
            multiFileCodeResult.setHtmlCode(htmlCode);
        }
        //设置css代码
        if (cssCode != null&&!cssCode.trim().isEmpty()) {
            multiFileCodeResult.setCssCode(cssCode);
        }
        //设置js代码
        if (jsCode != null&&!jsCode.trim().isEmpty()) {
            multiFileCodeResult.setJsCode(jsCode);
        }
        // 提取最后一个代码块之后的总结内容
        String description = CodeBlockExtractor.extractSummaryAfterLastCodeBlock(codeContent);
        if (description != null && !description.trim().isEmpty()) {
            multiFileCodeResult.setDescription(description.trim());
        }
        return multiFileCodeResult;
    }

}
