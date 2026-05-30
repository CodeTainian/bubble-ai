package com.bubble.bubbleai.core;

import com.bubble.bubbleai.ai.AiCodeGeneratorService;
import com.bubble.bubbleai.ai.model.HtmlCodeResult;
import com.bubble.bubbleai.ai.model.MultiFileCodeResult;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.core.parser.CodeParserExecutor;
import com.bubble.bubbleai.core.saver.CodeFileSaveExecutor;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 采用门面设计模式，通过统一的入口，处理不同的生成文件类型
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

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
    public Flux<String> generateAndSaveCodeStream(String userMassage,CodeGenTypeEnum codeGenTypeEnum,Long appId) throws BusinessException {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"生成类型为空");
        }
        return switch (codeGenTypeEnum){
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMassage);
                yield processCodeStream(codeStream,CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FIlE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMassage);
                yield processCodeStream(codeStream,CodeGenTypeEnum.MULTI_FIlE,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型"+ codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,errorMessage);
            }
        };
    }

    /**
     * 通用流式代码处理方式
     * @param codeStream 代码刘留
     * @param codeGenTypeEnum 代码生成类型
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

    /**
     * 生成单个文件并保存
     * @param userMassage 用户消息
     * @return 保存的目录
     */
//    private File generateAndSaveHtmlCode(String userMassage){
//        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMassage);
//        return CodeFileSaver.saveHtmlCodeResult(result);
//    }

    /**
     * 生成多个文件并保存
     * @param userMassage 用户消息
     * @return 保存的目录
     */
//    private File generateAndSaveMultiFileCode(String userMassage){
//        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMassage);
//        return CodeFileSaver.saveMultiFIleCodeResult(result);
//    }

}
