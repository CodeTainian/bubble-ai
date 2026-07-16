package com.bubble.bubbleai.core.handler;

import com.bubble.bubbleai.ai.tools.BaseTool;
import com.bubble.bubbleai.ai.tools.ToolManager;
import com.bubble.bubbleai.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class JsonMessageStreamHandlerTest {

    @Test
    void toolSseDetailsEnterDisplayStreamButDoNotEnterModelContent() {
        ToolManager tools = mock(ToolManager.class);
        BaseTool writeTool = mock(BaseTool.class);
        when(tools.getTool("writeFile")).thenReturn(writeTool);
        when(writeTool.getDisplayName()).thenReturn("写入文件");
        when(writeTool.generateToolRequestResponse()).thenReturn("\n\n[选择工具] 写入文件\n\n");
        when(writeTool.generateToolExecutedResult(any())).thenReturn(
                "[工具调用] 写入文件 src/App.jsx\n```jsx\nconst API_KEY = 'demo';\n```");
        JsonMessageStreamHandler handler = new JsonMessageStreamHandler(
                mock(ChatHistoryService.class), mock(AppCoverGenerator.class), tools);
        StringBuilder modelContent = new StringBuilder();

        List<String> displayed = handler.transform(Flux.just(
                "{\"type\":\"ai_response\",\"data\":\"正在生成。\"}",
                "{\"type\":\"tool_request\",\"id\":\"t1\",\"name\":\"writeFile\",\"argument\":\"secret\"}",
                "{\"type\":\"tool_executed\",\"id\":\"t1\",\"name\":\"writeFile\",\"argument\":\"{\\\"content\\\":\\\"API_KEY=secret\\\"}\"}"
        ), modelContent).collectList().block();

        assertEquals("正在生成。", modelContent.toString());
        assertEquals(List.of("正在生成。", "\n\n[选择工具] 写入文件\n\n",
                "\n\n[工具调用] 写入文件 src/App.jsx\n```jsx\nconst API_KEY = 'demo';\n```\n\n"), displayed);
        verify(writeTool).generateToolExecutedResult(any());
    }
}
