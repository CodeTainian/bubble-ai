package com.bubble.bubbleai.core;

import com.bubble.bubbleai.ai.AiCodeGeneratorService;
import com.bubble.bubbleai.ai.AiCodeGeneratorServiceFactory;
import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.core.parser.CodeParserExecutor;
import com.bubble.bubbleai.core.saver.CodeFileSaveExecutor;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 采用门面设计模式，通过统一的入口，处理不同的生成文件类型
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 统一入口，根据不同类型生成并保存代码(标准形式)
     * @param userMassage 用户类型
     * @param codeGenTypeEnum 代码类型
     * @param appId 应用ID
     * @return 保存的文件
     */
    public File generateAndSaveCode(String userMassage, CodeGenTypeEnum codeGenTypeEnum,Long appId) throws BusinessException {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"生成类型为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        return switch (codeGenTypeEnum){
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMassage);
                 yield CodeFileSaveExecutor.executeSaver(result,CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FIlE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMassage);
                yield  CodeFileSaveExecutor.executeSaver(result,CodeGenTypeEnum.MULTI_FIlE,appId);

            }
            default -> {
                String errorMessage = "不支持的类型；"+ codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,errorMessage);
            }
        };
    }
    

    /**
     * 统一入口: 根据类型生成并保存代码(流式)
     * @param userMassage 用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId 应用ID
     * @return 生成的代码片段
     */
    public Flux<ServerSentEvent<String>> generateAndSaveCodeStream(String userMassage,CodeGenTypeEnum codeGenTypeEnum,Long appId) throws BusinessException {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"生成类型为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenTypeEnum);
        return switch (codeGenTypeEnum){
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMassage);
                yield processCodeStream(codeStream,CodeGenTypeEnum.HTML,appId).
                        map(chunk->ServerSentEvent.
                                <String>builder().event("ai_text").data(chunk).build());
            }
            case MULTI_FIlE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMassage);
                yield processCodeStream(codeStream,CodeGenTypeEnum.MULTI_FIlE,appId).
                        map(chunk->ServerSentEvent.
                                <String>builder().event("ai_text").data(chunk).build());
            }
            case REACT_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateReactProjectCodeStream(appId, userMassage);
                yield processReactProjectTokenStream(tokenStream,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型"+ codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,errorMessage);
            }
        };
    }

    /**
     * 通用流式代码处理方式
     * @param codeStream；
     * @param codeGenTypeEnum；
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream,CodeGenTypeEnum codeGenTypeEnum,Long appId) throws BusinessException {
        StringBuilder codeBuilder = new StringBuilder();
        //实时收集代码片段
        return codeStream.doOnNext(chunk->{
            codeBuilder.append(chunk);
        }).doOnComplete(()->{
                //流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                //使用执行器解析代码
                Object parserResult = CodeParserExecutor.executeParser(completeCode, codeGenTypeEnum);
                //使用解析器保存代码
                File saveDir = CodeFileSaveExecutor.executeSaver(parserResult, codeGenTypeEnum,appId);
                log.info("保存成功，路径为:{}", saveDir.getAbsolutePath());
            }catch (Exception e){
                log.info("保存失败: {}",e.getMessage());
            }
        });
    }

    private Flux<ServerSentEvent<String>> processReactProjectTokenStream(TokenStream tokenStream, Long appId) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicInteger stepCounter = new AtomicInteger(0);
        tokenStream.onPartialResponse(token -> {
                    emit(sink, "ai_text", Map.of(
                            "type", "ai_text",
                            "content", token
                    ));
                })
                .beforeToolExecution(beforeToolExecution -> {
                    var request = beforeToolExecution.request();

                    String toolName = request.name();
                    String arguments = request.arguments();

                    Map<String, Object> args = parseToolArguments(arguments);

                    String path = getString(args, "path");
                    if (path.isBlank()) {
                        path = getString(args, "fileName");
                    }

                    String description = getString(args, "description");
                    String content = getString(args, "content");

                    int step = stepCounter.incrementAndGet();

                    Map<String, Object> stepData = new LinkedHashMap<>();
                    stepData.put("type", "step");
                    stepData.put("step", step);
                    stepData.put("title", "STEP " + step + "：" + description);
                    stepData.put("path", path);
                    emit(sink, "step", stepData);

                    Map<String, Object> toolStartData = new LinkedHashMap<>();
                    toolStartData.put("type", "tool_start");
                    toolStartData.put("step", step);
                    toolStartData.put("toolName", toolName);
                    toolStartData.put("path", path);
                    toolStartData.put("description", description);
                    emit(sink, "tool_start", toolStartData);

                    Map<String, Object> fileWriteData = new LinkedHashMap<>();
                    fileWriteData.put("type", "file_write");
                    fileWriteData.put("step", step);
                    fileWriteData.put("fileName", getFileName(path));
                    fileWriteData.put("path", path);
                    fileWriteData.put("language", guessLanguage(path));
                    fileWriteData.put("description", description);
                    fileWriteData.put("content", content);
                    emit(sink, "file_write", fileWriteData);
                })

                .onToolExecuted(toolExecution -> {
                    var request = toolExecution.request();

                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", "tool_done");
                    data.put("toolName", request.name());
                    data.put("result", String.valueOf(toolExecution.result()));

                    emit(sink, "tool_done", data);
                })

                .onCompleteResponse(response -> {
                    emit(sink, "done", Map.of(
                            "type", "done",
                            "message", "生成完成",
                            "appId", appId
                    ));

                    sink.tryEmitComplete();
                })

                .onError(error -> {
                    emit(sink, "generation_error", Map.of(
                            "type", "generation_error",
                            "message", error.getMessage()
                    ));

                    sink.tryEmitComplete();
                })

                .start();

        return sink.asFlux();
    }

    private void emit(Sinks.Many<ServerSentEvent<String>> sink, String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);

            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event(eventName)
                    .data(json)
                    .build());
        } catch (Exception e) {
            sink.tryEmitError(e);
        }
    }

    private Map<String, Object> parseToolArguments(String arguments) {
        try {
            return objectMapper.readValue(
                    arguments,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rawArguments", arguments);
            return map;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String getFileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int index = path.lastIndexOf("/");
        return index >= 0 ? path.substring(index + 1) : path;
    }

    private String guessLanguage(String path) {
        if (path == null) {
            return "text";
        }
        if (path.endsWith(".jsx")) return "jsx";
        if (path.endsWith(".tsx")) return "tsx";
        if (path.endsWith(".js")) return "javascript";
        if (path.endsWith(".ts")) return "typescript";
        if (path.endsWith(".css")) return "css";
        if (path.endsWith(".json")) return "json";
        if (path.endsWith(".html")) return "html";
        return "text";
    }
    /*
     * 生成单个文件并保存
     * @param userMassage 用户消息
     * @return 保存的目录
     */
//    private File generateAndSaveHtmlCode(String userMassage){
//        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMassage);
//        return CodeFileSaver.saveHtmlCodeResult(result);
//    }

    /*
     * 生成多个文件并保存
     * @param userMassage 用户消息
     * @return 保存的目录
     */
//    private File generateAndSaveMultiFileCode(String userMassage){
//        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMassage);
//        return CodeFileSaver.saveMultiFIleCodeResult(result);
//    }

}
