package com.bubble.bubbleai.core.handler;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.core.builder.BuildResult;
import com.bubble.bubbleai.core.builder.ReactProjectBuildRepairService;
import com.bubble.bubbleai.core.builder.ReactProjectBuilder;
import com.bubble.bubbleai.model.entity.User;
import com.bubble.bubbleai.model.enums.ChatHistoryMessageTypeEnum;
import com.bubble.bubbleai.model.enums.ChatMessageSource;
import com.bubble.bubbleai.monitor.MonitorContext;
import com.bubble.bubbleai.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StreamTranscriptPersistenceTest {

    @Test
    void persistsDisplayedBuildStatusesButKeepsModelContentClean() {
        ChatHistoryService histories = mock(ChatHistoryService.class);
        AppCoverGenerator covers = mock(AppCoverGenerator.class);
        ReactProjectBuilder builder = mock(ReactProjectBuilder.class);
        GenerationStateService states = mock(GenerationStateService.class);
        TestReactHandler handler = new TestReactHandler(histories, covers);
        ReflectionTestUtils.setField(handler, "reactProjectBuilder", builder);
        ReflectionTestUtils.setField(handler, "reactProjectBuildRepairService",
                mock(ReactProjectBuildRepairService.class));
        ReflectionTestUtils.setField(handler, "generationStateService", states);
        when(builder.buildProjectWithResultAsync(anyString())).thenReturn(
                CompletableFuture.completedFuture(
                        BuildResult.success("npm_build", "npm run build", "ok", "", 10)));
        User user = new User();
        user.setId(7L);
        StreamHandleContext context = new StreamHandleContext(
                Flux.just("项目已生成完毕。"), 9L, user, CodeGenTypeEnum.REACT_PROJECT,
                "generation-1", "生成页面", MonitorContext.system());

        handler.handle(context).collectList().block();

        verify(histories).addChatMessage(eq(9L), eq(7L),
                eq(ChatHistoryMessageTypeEnum.AI.getValue()),
                argThat(display -> display.contains("项目已生成完毕。")
                        && display.contains("正在检查项目构建结果")
                        && display.contains("项目构建成功")),
                eq("项目已生成完毕。"), eq(ChatMessageSource.AI_OUTPUT),
                eq(true), isNull(), isNull());
    }

    private static final class TestReactHandler extends AbstractStreamHandler {
        private TestReactHandler(ChatHistoryService histories, AppCoverGenerator covers) {
            super(histories, covers);
        }

        @Override
        public boolean supports(CodeGenTypeEnum codeGenType) {
            return codeGenType == CodeGenTypeEnum.REACT_PROJECT;
        }

        @Override
        protected Flux<String> transform(Flux<String> originFlux, StringBuilder modelBuilder) {
            return originFlux.doOnNext(modelBuilder::append);
        }
    }
}
