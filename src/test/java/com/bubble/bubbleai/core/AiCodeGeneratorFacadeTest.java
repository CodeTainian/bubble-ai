package com.bubble.bubbleai.core;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
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
        Flux<ServerSentEvent<String>> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("生成一个简单任务记录网站", CodeGenTypeEnum.MULTI_FIlE,1L);
        List<ServerSentEvent<String>> parts = codeStream.collectList().block();
        Assertions.assertNotNull(parts);

        // 打印所有 SSE 事件，方便你观察后端到底推了什么
        for (ServerSentEvent<String> event : parts) {
            System.out.println("event = " + event.event());
            System.out.println("data = " + event.data());
            System.out.println("----------------------");
        }
        // 至少应该有 done 事件
        boolean hasDoneEvent = parts.stream()
                .anyMatch(event -> "done".equals(event.event()));
        Assertions.assertTrue(hasDoneEvent);
    }

    @Test
    void generateReactProjectCodeStream() {
        Flux<ServerSentEvent<String>> eventStream =
                aiCodeGeneratorFacade.generateAndSaveCodeStream(
                        "简单的任务管理网站，不超过200行代码",
                        CodeGenTypeEnum.REACT_PROJECT,
                        1L
                );

        List<ServerSentEvent<String>> events = eventStream.collectList().block();

        Assertions.assertNotNull(events);
        Assertions.assertFalse(events.isEmpty());

        // 打印所有 SSE 事件，方便你观察后端到底推了什么
        for (ServerSentEvent<String> event : events) {
            System.out.println("event = " + event.event());
            System.out.println("data = " + event.data());
            System.out.println("----------------------");
        }

        // 至少应该有 done 事件
        boolean hasDoneEvent = events.stream()
                .anyMatch(event -> "done".equals(event.event()));

        Assertions.assertTrue(hasDoneEvent);
    }

}