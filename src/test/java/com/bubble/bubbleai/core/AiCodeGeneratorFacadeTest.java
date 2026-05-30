package com.bubble.bubbleai.core;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("任务记录网站", CodeGenTypeEnum.MULTI_FIlE,1L);
        Assertions.assertNotNull(file);
    }

    @Test
    void generateAndSaveCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("生成一个简单任务记录网站", CodeGenTypeEnum.MULTI_FIlE,1L);
        List<String> parts = codeStream.collectList().block();
        Assertions.assertNotNull(parts);
        String completeContent = String.join("", parts);
        Assertions.assertNotNull(completeContent);
    }
}