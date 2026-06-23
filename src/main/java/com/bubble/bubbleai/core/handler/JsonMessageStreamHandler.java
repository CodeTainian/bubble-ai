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

    private static final String RELATIVE_FILE_PATH_FIELD = "relativeFilePath";

    private static final String RELATIVE_PATH_FIELD = "relativePath";

    private static final String CONTENT_FIELD = "content";

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
                ToolFileInfo toolFileInfo = parseToolFileInfo(toolRequestMessage.getArgument());
                if (StrUtil.isNotBlank(toolId)
                        && StrUtil.isNotBlank(toolFileInfo.relativePath())
                        && seenToolIds.add(toolId)) {
                    return String.format("\n\n[选择工具] 写入文件 %s\n\n", toolFileInfo.relativePath());
                }
                return "";
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                ToolFileInfo toolFileInfo = parseToolFileInfo(toolExecutedMessage.getArgument());
                String relativePath = toolFileInfo.relativePath();
                if (StrUtil.isBlank(relativePath)) {
                    relativePath = parseRelativePathFromResult(toolExecutedMessage.getResult());
                }
                if (StrUtil.isBlank(relativePath)) {
                    log.warn("工具执行结果缺少文件路径, toolId={}, result={}",
                            toolExecutedMessage.getId(), toolExecutedMessage.getResult());
                    return "";
                }
                String suffix = FileUtil.getSuffix(relativePath);
                if (suffix == null) {
                    suffix = "";
                }
                String content = toolFileInfo.content();
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

    private ToolFileInfo parseToolFileInfo(String argument) {
        if (StrUtil.isBlank(argument)) {
            return ToolFileInfo.empty();
        }
        String json = argument.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return ToolFileInfo.empty();
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(json);
            String relativePath = jsonObject.getStr(RELATIVE_FILE_PATH_FIELD);
            if (StrUtil.isBlank(relativePath)) {
                relativePath = jsonObject.getStr(RELATIVE_PATH_FIELD);
            }
            String content = jsonObject.getStr(CONTENT_FIELD);
            return new ToolFileInfo(relativePath, content);
        } catch (Exception e) {
            log.warn("解析工具调用参数失败: {}", json, e);
            return ToolFileInfo.empty();
        }
    }

    private String parseRelativePathFromResult(String result) {
        if (StrUtil.isBlank(result)) {
            return "";
        }
        String successPrefix = "文件写入成功:";
        String failPrefix = "文件写入失败:";
        if (result.startsWith(successPrefix)) {
            return result.substring(successPrefix.length()).trim();
        }
        if (result.startsWith(failPrefix)) {
            return result.substring(failPrefix.length()).trim();
        }
        return "";
    }

    private record ToolFileInfo(String relativePath, String content) {

        private static ToolFileInfo empty() {
            return new ToolFileInfo("", "");
        }
    }


}
