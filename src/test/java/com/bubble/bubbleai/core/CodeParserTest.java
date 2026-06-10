package com.bubble.bubbleai.core;

import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.core.parser.CodeParserExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeParserTest {


    @Test
    void parseHtmlCode() {
        String codeContent = """
                随便写一段描述：
                html 格式
                <!DOCTYPE html>
                <html>
                <head>
                    <title>测试页面</title>
                </head>
                <body>
                    <h1>Hello World!</h1>
                </body>
                </html>

                随便写一段描述
                """;
        HtmlCodeResult result = CodeParser.parseHtmlCode(codeContent);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
    }

    @Test
    void parseMultiFileCode() {
        String codeContent = """
                创建一个完整的网页：
                ```html
                <!DOCTYPE html>
                <html>
                <head>
                    <title>多文件示例</title>
                    <link rel="stylesheet" href="style.css">
                </head>
                <body>
                    <h1>欢迎使用</h1>
                    <script src="script.js"></script>
                </body>
                </html>
                ```

                ```css
                h1 {
                    color: blue;
                    text-align: center;
                }
                ```
                
                ```js
                console.log('页面加载完成');
                ```

                文件创建完成！
                """;

        MultiFileCodeResult result = CodeParser.parseMultiFileCode(codeContent);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
        assertNotNull(result.getCssCode());
        assertNotNull(result.getJsCode());
    }

    @Test
    void parseHtmlCodeSkipsFilenameOnlyCodeBlock() {
        String codeContent = """
                以下是您需要的网站代码。

                ```html
                index.html
                ```

                ```html
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <title>减肥记录</title>
                </head>
                <body>
                    <main>
                        <h1>6+18 任务记录</h1>
                    </main>
                </body>
                </html>
                ```
                """;

        HtmlCodeResult result = (HtmlCodeResult) CodeParserExecutor.executeParser(codeContent, CodeGenTypeEnum.HTML);

        assertNotNull(result);
        assertTrue(result.getHtmlCode().contains("<!DOCTYPE html>"));
        assertTrue(result.getHtmlCode().contains("<main>"));
        assertNotEquals("index.html", result.getHtmlCode().trim());
    }

    @Test
    void parseMultiFileCodeSkipsFilenameOnlyCodeBlocks() {
        String codeContent = """
                index.html
                ```html
                index.html
                ```

                ```html
                <!DOCTYPE html>
                <html>
                <head>
                    <link rel="stylesheet" href="style.css">
                </head>
                <body>
                    <button id="saveBtn">保存</button>
                    <script src="script.js"></script>
                </body>
                </html>
                ```

                style.css
                ```css
                style.css
                ```

                ```css
                body {
                    margin: 0;
                    color: #123;
                }
                ```

                script.js
                ```javascript
                script.js
                ```

                ```javascript
                const saveBtn = document.querySelector('#saveBtn');
                saveBtn.addEventListener('click', () => {
                    window.alert('已保存');
                });
                ```
                """;

        MultiFileCodeResult result = (MultiFileCodeResult) CodeParserExecutor.executeParser(codeContent, CodeGenTypeEnum.MULTI_FIlE);

        assertNotNull(result);
        assertTrue(result.getHtmlCode().contains("<!DOCTYPE html>"));
        assertTrue(result.getCssCode().contains("body {"));
        assertTrue(result.getJsCode().contains("addEventListener"));
        assertNotEquals("index.html", result.getHtmlCode().trim());
        assertNotEquals("style.css", result.getCssCode().trim());
        assertNotEquals("script.js", result.getJsCode().trim());
    }

    @Test
    void parseHtmlCodeStripsFilenameInsideCodeBlock() {
        String codeContent = """
                ```html
                index.html
                <!DOCTYPE html>
                <html>
                <body>
                    <h1>Hello</h1>
                </body>
                </html>
                ```
                """;

        HtmlCodeResult result = CodeParser.parseHtmlCode(codeContent);

        assertTrue(result.getHtmlCode().startsWith("<!DOCTYPE html>"));
        assertFalse(result.getHtmlCode().startsWith("index.html"));
    }
}
