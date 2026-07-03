package com.bubble.bubbleaiapp.core;

import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;
import com.bubble.bubbleaiapp.core.parser.CodeBlockExtractor;

/**
 * 代码解析器
 * 提供静态方法解析不同类型的代码内容
 */
public class CodeParser {

    /**
     * 解析html单文件代码
     * @param codeContent 原始内容
     * @return html代码
     */
    public static HtmlCodeResult parseHtmlCode(String codeContent) {
        HtmlCodeResult htmlCodeResult = new HtmlCodeResult();
        //提取html代码
        String extractedHtmlCode = CodeBlockExtractor.extractHtmlCode(codeContent);
        if (extractedHtmlCode != null&&!extractedHtmlCode.trim().isEmpty()) {
            htmlCodeResult.setHtmlCode(extractedHtmlCode);
        }else {
            htmlCodeResult.setHtmlCode(codeContent);
        }
        return htmlCodeResult;
    }

    public static MultiFileCodeResult parseMultiFileCode(String codeContent) {
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
        return multiFileCodeResult;
    }
}
