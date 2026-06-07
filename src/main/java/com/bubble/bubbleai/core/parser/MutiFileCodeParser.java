package com.bubble.bubbleai.core.parser;

import com.bubble.bubbleai.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MutiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    /**
     * 匹配所有 Markdown 代码块
     */
    private static final Pattern ANY_CODE_BLOCK_PATTERN = Pattern.compile("```[a-zA-Z]*\\s*\\n[\\s\\S]*?```", Pattern.CASE_INSENSITIVE);

    /**
     * 提取多个文件
     * @param codeContent;
     * @return 提取后的文件内容
     */
    @Override
    public MultiFileCodeResult parserCode(String codeContent) {
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
        // 提取最后一个代码块之后的总结内容
        String description = extractSummaryAfterLastCodeBlock(codeContent);
        if (description != null && !description.trim().isEmpty()) {
            multiFileCodeResult.setDescription(description.trim());
        }
        return multiFileCodeResult;
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

    /**
     * 如果最后的总结内容是Markdown格式，本方法支持提 Markdown 代码块之后的内容
     * 例如：
     * ```javascript
     * ...
     * ```
     * 这个实现满足以下要求：
     * ...
     * 使用说明：
     * ...
     */
    private static String extractSummaryAfterLastCodeBlock(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        Matcher matcher = ANY_CODE_BLOCK_PATTERN.matcher(content);
        int lastCodeBlockEndIndex = -1;

        while (matcher.find()) {
            lastCodeBlockEndIndex = matcher.end();
        }

        if (lastCodeBlockEndIndex == -1 || lastCodeBlockEndIndex >= content.length()) {
            return null;
        }

        return content.substring(lastCodeBlockEndIndex).trim();
    }

}
