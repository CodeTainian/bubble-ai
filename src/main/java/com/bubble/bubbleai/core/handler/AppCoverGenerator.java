package com.bubble.bubbleai.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.constant.CaptureConstant;
import com.bubble.bubbleai.mapper.AppMapper;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleai.service.ScreenshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * 应用封面生成器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppCoverGenerator {

    private final ScreenshotService screenshotService;

    private final AppMapper appMapper;

    /**
     * 异步生成应用封面，失败不影响主流程。
     *
     * @param appId       应用 ID
     * @param codeGenType 代码生成类型
     */
    public void generateAsync(Long appId, CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            log.warn("skip generating app cover because codeGenType is null, appId={}", appId);
            return;
        }
        CompletableFuture.runAsync(() -> generate(appId, codeGenType.getValue()))
                .exceptionally(error -> {
                    log.error("generate app cover failed, appId={}", appId, error);
                    return null;
                });
    }

    /**
     * 生成应用封面并回写应用表。
     *
     * @param appId       应用 ID
     * @param codeGenType 代码生成类型
     */
    public void generate(Long appId, String codeGenType) {
        if (appId == null || appId <= 0 || StrUtil.isBlank(codeGenType)) {
            log.warn("skip generating app cover because params are invalid, appId={}, codeGenType={}", appId, codeGenType);
            return;
        }
        String sourceDirName = codeGenType + "_" + appId;
        String previewUrl = String.format("%s/%s/", CaptureConstant.CAPTURE_PREVIEW_COVER, sourceDirName);
        String coverFileName = sourceDirName + ".png";
        FileUtil.mkdir(CaptureConstant.CAPTURE_OUTPUT_COVER);
        String coverSavePath = CaptureConstant.CAPTURE_OUTPUT_COVER + File.separator + coverFileName;

        screenshotService.captureHomePage(previewUrl, coverSavePath);

        String coverUrl = CaptureConstant.CAPTURE_HOST + "/output_covers/" + coverFileName;
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCover(coverUrl);
        int updated = appMapper.update(updateApp);
        if (updated <= 0) {
            log.warn("app cover generated but database update failed, appId={}, coverUrl={}", appId, coverUrl);
        }
    }
}
