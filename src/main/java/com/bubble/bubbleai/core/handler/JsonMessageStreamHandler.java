package com.bubble.bubbleai.core.handler;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.ai.model.message.*;
import com.bubble.bubbleai.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 流消息处理器
 */
@Slf4j
@Component
public class JsonMessageStreamHandler extends AbstractStreamHandler {

    public JsonMessageStreamHandler(ChatHistoryService chatHistoryService, AppCoverGenerator appCoverGenerator) {
        super(chatHistoryService, appCoverGenerator);
    }

    @Override
    public boolean supports(CodeGenTypeEnum codeGenType) {
        return CodeGenTypeEnum.REACT_PROJECT.equals(codeGenType);
    }

    @Override
    protected Flux<String> transform(Flux<String> originFlux, StringBuilder aiMessageBuilder) {
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> handleJsonMessageChunk(chunk, aiMessageBuilder, seenToolIds))
                .filter(StrUtil::isNotEmpty);
    }

    private String handleJsonMessageChunk(String chunk, StringBuilder streamBuilder, Set<String> seenToolIds) {
        if (StrUtil.isBlank(chunk)) {
            return "";
        }
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            log.warn("不支持的消息类型: {}", streamMessage.getType());
            return "";
        }

        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiResponseMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiResponseMessage.getData();
                if (StrUtil.isBlank(data)) {
                    return "";
                }
                streamBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                if (StrUtil.isNotBlank(toolId) && seenToolIds.add(toolId)) {
                    return "\n\n[选择工具] 写入文件\n\n";
                }
                return "";
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArgument());
                String relativePath = jsonObject.getStr("relativePath");
                if (StrUtil.isBlank(relativePath)) {
                    relativePath = "unknown";
                }
                String suffix = FileUtil.getSuffix(relativePath);
                if (suffix == null) {
                    suffix = "";
                }
                String content = jsonObject.getStr("content");
                if (content == null) {
                    content = "";
                }
                String result = String.format("""
                        [工具调用] 写入文件 %s
                        ```%s
                        %s
                        ```
                        """, relativePath, suffix, content);
                String format = String.format("\n\n%s\n\n", result);
                streamBuilder.append(format);
                return format;
            }
        }
        return "";
    }


}
