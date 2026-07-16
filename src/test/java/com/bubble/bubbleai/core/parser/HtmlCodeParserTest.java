package com.bubble.bubbleai.core.parser;

import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlCodeParserTest {

    private final HtmlCodeParser parser = new HtmlCodeParser();

    @Test
    void parserCodeShouldExtractHtmlCodeBlock() {
        String content = """
                下面是页面代码：

                ```html
                <!DOCTYPE html>
                <html>
                <body><h1>Hello</h1></body>
                </html>
                ```

                已完成。
                """;

        HtmlCodeResult result = parser.parserCode(content);

        assertThat(result.getHtmlCode())
                .contains("<h1>Hello</h1>")
                .doesNotContain("下面是页面代码");
    }

    @Test
    void parserCodeShouldExtractRawHtmlDocument() {
        String content = """
                我直接给你完整 HTML：
                <!DOCTYPE html>
                <html>
                <body><main>Raw page</main></body>
                </html>
                说明文本不应进入文件。
                """;

        HtmlCodeResult result = parser.parserCode(content);

        assertThat(result.getHtmlCode())
                .startsWith("<!DOCTYPE html>")
                .contains("<main>Raw page</main>")
                .doesNotContain("说明文本不应进入文件");
    }

    @Test
    void parserCodeShouldUseDefaultPreviewWhenNoHtmlCanBeExtracted() {
        String content = "这个需求还不够明确，请补充网站主题、配色和需要的模块。";

        HtmlCodeResult result = parser.parserCode(content);

        assertThat(result.getHtmlCode())
                .contains("暂未生成可预览页面")
                .doesNotContain(content);
    }
}
