package com.bubble.bubbleaiapp.core.handler;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.ai.model.message.*;
import com.bubble.bubbleai.ai.tools.BaseTool;
import com.bubble.bubbleai.ai.tools.ToolManager;
import com.bubble.bubbleaiapp.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 流消息处理器。
 *
 * <p>React 项目生成时，AI 原始响应不是普通文本，而是一段段 JSON 消息。
 * 每段消息会先用 {@link StreamMessage#getType()} 标识自己的类型，再携带对应的数据：
 * 普通 AI 回复、工具请求、工具执行结果等。</p>
 *
 * <p>这个处理器只负责三件事：</p>
 * <ul>
 *     <li>把原始 JSON 消息解析成具体消息对象；</li>
 *     <li>把需要展示给前端的内容转换成文本流；</li>
 *     <li>把最终需要保存进聊天历史的 AI 内容追加到 {@code aiMessageBuilder}。</li>
 * </ul>
 *
 * <p>工具本身的展示格式仍然交给 {@link BaseTool} 的实现类处理，
 * 这样新增工具时不需要继续扩展本 handler 的 switch 分支。</p>
 */
@Slf4j
@Component
public class JsonMessageStreamHandler extends AbstractStreamHandler {

    /**
     * LangChain4j 工具参数中常用的文件路径字段名，例如 writeFile/modifyFile。
     */
    private static final String RELATIVE_FILE_PATH_FIELD = "relativeFilePath";

    /**
     * 部分工具或历史消息中可能使用的路径字段别名，例如 deleteFile。
     */
    private static final String RELATIVE_PATH_FIELD = "relativePath";

    /**
     * 工具管理器用于通过工具英文名拿到对应的工具实现。
     */
    private final ToolManager toolManager;

    public JsonMessageStreamHandler(ChatHistoryService chatHistoryService, AppCoverGenerator appCoverGenerator, ToolManager toolManager) {
        super(chatHistoryService, appCoverGenerator);
        this.toolManager = toolManager;
    }

    @Override
    public boolean supports(CodeGenTypeEnum codeGenType) {
        return CodeGenTypeEnum.REACT_PROJECT.equals(codeGenType);
    }

    @Override
    protected Flux<String> transform(Flux<String> originFlux, StringBuilder aiMessageBuilder) {
        /*
         * 同一个工具调用在流式过程中可能被重复推送。
         * 这里按 tool request id 去重，避免前端重复显示“选择工具”提示。
         */
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> handleJsonMessageChunk(chunk, aiMessageBuilder, seenToolIds))
                .filter(StrUtil::isNotEmpty);
    }

    /**
     * 处理单个 JSON 流消息片段。
     *
     * <p>这里先只解析最小公共结构 {@link StreamMessage} 来识别消息类型，
     * 再分发给对应的具体处理方法。这样做可以避免一开始就把所有字段揉在一起，
     * 也让不同消息类型的处理边界更清楚。</p>
     *
     * @param chunk         AI 返回的单个 JSON 字符串片段
     * @param streamBuilder 用于累计最终保存到聊天历史的 AI 回复
     * @param seenToolIds   已经展示过的工具请求 id 集合
     * @return 需要继续推送给前端的文本；空字符串表示该片段不需要展示
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder streamBuilder, Set<String> seenToolIds) {
        if (StrUtil.isBlank(chunk)) {
            return "";
        }
        StreamMessage streamMessage = parseMessage(chunk, StreamMessage.class);
        if (streamMessage == null) {
            return "";
        }
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            log.warn("不支持的消息类型: {}", streamMessage.getType());
            return "";
        }

        return switch (typeEnum) {
            case AI_RESPONSE -> handleAiResponse(chunk, streamBuilder);
            case TOOL_REQUEST -> handleToolRequest(chunk, seenToolIds);
            case TOOL_EXECUTED -> handleToolExecuted(chunk, streamBuilder);
        };
    }

    /**
     * 处理普通 AI 文本回复。
     *
     * <p>这类消息的内容既要实时返回给前端，也要追加到 {@code streamBuilder}，
     * 因为 {@link AbstractStreamHandler} 会在流结束后统一把 builder 中的内容保存到聊天历史。</p>
     */
    private String handleAiResponse(String chunk, StringBuilder streamBuilder) {
        AiResponseMessage aiResponseMessage = parseMessage(chunk, AiResponseMessage.class);
        if (aiResponseMessage == null || StrUtil.isBlank(aiResponseMessage.getData())) {
            return "";
        }
        String data = aiResponseMessage.getData();
        streamBuilder.append(data);
        return data;
    }

    /**
     * 处理工具请求消息。
     *
     * <p>工具请求表示 AI 准备调用某个工具，但工具还没有执行完成。
     * 对用户来说，这里适合展示类似“选择工具：写入文件”的轻量提示，
     * 不适合写入聊天历史，否则历史记录会出现一堆过程性提示。</p>
     */
    private String handleToolRequest(String chunk, Set<String> seenToolIds) {
        ToolRequestMessage toolRequestMessage = parseMessage(chunk, ToolRequestMessage.class);
        if (toolRequestMessage == null || StrUtil.isBlank(toolRequestMessage.getId())) {
            return "";
        }
        if (!seenToolIds.add(toolRequestMessage.getId())) {
            return "";
        }
        BaseTool tool = getTool(toolRequestMessage.getName());
        if (tool == null) {
            return "";
        }
        try {
            // 展示文案交给具体工具生成，handler 不关心工具的中文名称或展示风格。
            return tool.generateToolRequestResponse();
        } catch (Exception e) {
            log.warn("生成工具请求展示文本失败, toolName={}", toolRequestMessage.getName(), e);
            return "";
        }
    }

    /**
     * 处理工具执行完成消息。
     *
     * <p>工具执行结果按 SSE 原展示格式进入 displayContent，保证刷新前后体验一致；
     * modelContent 仍只累计普通 AI 文本，不会被工具参数污染。</p>
     */
    private String handleToolExecuted(String chunk, StringBuilder streamBuilder) {
        ToolExecutedMessage toolExecutedMessage = parseMessage(chunk, ToolExecutedMessage.class);
        if (toolExecutedMessage == null) {
            return "";
        }
        BaseTool tool = getTool(toolExecutedMessage.getName());
        if (tool == null) {
            return "";
        }
        /*
         * 工具参数保留为 JSONObject，并传回 BaseTool。
         * 这样每个工具可以按自己的参数结构生成展示文本，
         * handler 不再需要关心 writeFile、readFile、deleteFile 的字段差异。
         */
        JSONObject argument = parseToolArgument(toolExecutedMessage.getArgument());
        try {
            String result = tool.generateToolExecutedResult(argument);
            if (StrUtil.isBlank(result)) {
                return "";
            }
            // AbstractStreamHandler persists emitted chunks to displayContent;
            // streamBuilder remains model-only.
            return String.format("\n\n%s\n\n", result);
        } catch (Exception e) {
            log.warn("生成工具执行展示文本失败, toolName={}, argument={}",
                    toolExecutedMessage.getName(), toolExecutedMessage.getArgument(), e);
            return "";
        }
    }

    /**
     * 根据工具名称获取工具实例。
     *
     * <p>这里做一次统一空值检查和日志记录，避免每个分支都写重复的 null 判断。
     * 如果工具不存在，本次消息会被忽略，但不会中断整个响应流。</p>
     */
    private BaseTool getTool(String toolName) {
        if (StrUtil.isBlank(toolName)) {
            log.warn("工具消息缺少工具名称");
            return null;
        }
        BaseTool tool = toolManager.getTool(toolName);
        if (tool == null) {
            log.warn("工具未注册: {}", toolName);
        }
        return tool;
    }

    /**
     * 安全解析 JSON 消息。
     *
     * <p>流式响应中的单个片段如果格式异常，不能直接抛出异常打断整个 Flux。
     * 因此这里统一捕获解析异常，记录日志后返回 null，由调用方决定跳过当前片段。</p>
     */
    private <T> T parseMessage(String chunk, Class<T> messageClass) {
        try {
            return JSONUtil.toBean(chunk, messageClass);
        } catch (Exception e) {
            log.warn("解析流式消息失败, messageClass={}, chunk={}", messageClass.getSimpleName(), chunk, e);
            return null;
        }
    }

    /**
     * 解析工具调用参数。
     *
     * <p>LangChain4j 的工具参数本质上是一个 JSON 字符串。这里不把它转成某个固定 Java record，
     * 是因为不同工具的参数结构不同：写文件需要 content，修改文件需要 oldContent/newContent，
     * 读目录又使用 relativeDirPath。保留 {@link JSONObject} 可以让工具层自己解释参数。</p>
     *
     * <p>返回空 JSONObject 而不是 null，是为了让工具实现可以继续使用 getStr 等方法，
     * 减少调用方空指针判断。</p>
     */
    private JSONObject parseToolArgument(String argument) {
        if (StrUtil.isBlank(argument)) {
            return new JSONObject();
        }
        String json = argument.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            log.warn("工具调用参数不是 JSON 对象: {}", argument);
            return new JSONObject();
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(json);
            normalizeRelativePathField(jsonObject);
            return jsonObject;
        } catch (Exception e) {
            log.warn("解析工具调用参数失败: {}", json, e);
            return new JSONObject();
        }
    }

    /**
     * 统一工具参数中的路径字段别名。
     *
     * <p>目前工具里同时存在 {@code relativeFilePath} 和 {@code relativePath} 两种命名。
     * handler 在解析后把缺失的一方补齐，后续每个工具就可以继续使用自己习惯的字段名，
     * 也能兼容 AI 偶尔返回另一种字段名的情况。</p>
     */
    private void normalizeRelativePathField(JSONObject jsonObject) {
        String relativeFilePath = jsonObject.getStr(RELATIVE_FILE_PATH_FIELD);
        String relativePath = jsonObject.getStr(RELATIVE_PATH_FIELD);
        if (StrUtil.isBlank(relativeFilePath) && StrUtil.isNotBlank(relativePath)) {
            jsonObject.set(RELATIVE_FILE_PATH_FIELD, relativePath);
        }
        if (StrUtil.isBlank(relativePath) && StrUtil.isNotBlank(relativeFilePath)) {
            jsonObject.set(RELATIVE_PATH_FIELD, relativeFilePath);
        }
    }
}
