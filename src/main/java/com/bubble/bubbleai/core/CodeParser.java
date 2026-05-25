package com.bubble.bubbleai.core;

import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码解析器
 * 提供静态方法解析不同类型的代码内容
 */
public class CodeParser {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 解析html单文件代码
     * @param codeContent 原始内容
     * @return html代码
     */
    public static HtmlCodeResult parseHtmlCode(String codeContent) {
        HtmlCodeResult htmlCodeResult = new HtmlCodeResult();
        //提取html代码
        String extractedHtmlCode = extractHtmlCode(codeContent);
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
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        //css 代码
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        //js 代码
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);
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

    /**
     * 提取 html 代码
     * @param content 代码内容
     * @return Html代码
     */
    private static String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 根据正则模式提取代码
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 提取的代码
     */
    private static String extractCodeByPattern(String content,Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
