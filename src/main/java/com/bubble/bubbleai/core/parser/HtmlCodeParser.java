package com.bubble.bubbleai.core.parser;

import com.bubble.bubbleai.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlCodeParser implements CodeParser<HtmlCodeResult>{

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 解析html单文件代码
     * @param codeContent 原始内容
     * @return html代码
     */
    @Override
    public HtmlCodeResult parserCode(String codeContent) {
        HtmlCodeResult htmlCodeResult = new HtmlCodeResult();
        //提取html代码
        String extractedHtmlCode = extractHtmlCode(codeContent);
        if (extractedHtmlCode != null&&!extractedHtmlCode.trim().isEmpty()) {
            htmlCodeResult.setHtmlCode(extractedHtmlCode);
        }else {
            //如果没有提取到代码块，则将整个内容作为html
            htmlCodeResult.setHtmlCode(codeContent);
        }
        return htmlCodeResult;
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
}
