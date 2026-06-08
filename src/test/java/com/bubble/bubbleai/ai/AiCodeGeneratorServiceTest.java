package com.bubble.bubbleai.ai;

import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("生成一个20行的博客代码");
        Assertions.assertNotNull(result);
        result = aiCodeGeneratorService.generateHtmlCode("告诉我你刚刚干了啥子");
        Assertions.assertNotNull(result);
        result = aiCodeGeneratorService.generateHtmlCode("做一个关爱老人网站，不超过20行代码");
        Assertions.assertNotNull(result);
        result = aiCodeGeneratorService.generateHtmlCode("tell me what you have done");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode("生成一个不超过20行的简单的记账网站");
        Assertions.assertNotNull(result);
    }
}