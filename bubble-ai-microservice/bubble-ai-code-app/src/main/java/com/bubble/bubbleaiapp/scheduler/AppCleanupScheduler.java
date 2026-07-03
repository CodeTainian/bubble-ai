package com.bubble.bubbleaiapp.scheduler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.constant.CaptureConstant;
import com.bubble.bubbleai.model.entity.App;
import com.bubble.bubbleaiapp.repository.AppCleanupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 定时物理清理已经逻辑删除的应用及关联资源。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppCleanupScheduler {

    private final AppCleanupRepository appCleanupRepository;

    @Value("${app.cleanup.batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${app.cleanup.cron:0 0 3 * * ?}")
    public void cleanupDeletedApps() {
        int currentBatchSize = Math.max(batchSize, 1);
        List<App> deletedApps = appCleanupRepository.listDeletedApps(currentBatchSize);
        if (deletedApps.isEmpty()) {
            return;
        }
        log.info("开始清理已逻辑删除应用，数量={}", deletedApps.size());
        int successCount = 0;
        for (App app : deletedApps) {
            if (cleanupDeletedApp(app)) {
                successCount++;
            }
        }
        log.info("已逻辑删除应用清理完成，成功数量={}, 总数量={}", successCount, deletedApps.size());
    }

    private boolean cleanupDeletedApp(App app) {
        Long appId = app.getId();
        try {
            int chatDeletedCount = appCleanupRepository.physicalDeleteChatHistoryByAppId(appId);
            boolean coverDeleted = deleteAppCover(app);
            boolean deployDeleted = deleteDeployResource(app);
            if (!coverDeleted || !deployDeleted) {
                log.warn("应用资源清理未完成，跳过物理删除应用，appId={}", appId);
                return false;
            }
            int deletedCount = appCleanupRepository.physicalDeleteDeletedAppById(appId);
            if (deletedCount <= 0) {
                log.warn("物理删除应用失败或应用已不处于逻辑删除状态，appId={}", appId);
                return false;
            }
            log.info("物理清理应用成功，appId={}, chatDeletedCount={}", appId, chatDeletedCount);
            return true;
        } catch (Exception e) {
            log.error("物理清理应用失败，appId={}", appId, e);
            return false;
        }
    }

    private boolean deleteAppCover(App app) {
        Set<String> coverFileNames = resolveCoverFileNames(app);
        boolean deleted = true;
        for (String fileName : coverFileNames) {
            deleted = deleteResource(CaptureConstant.CAPTURE_OUTPUT_COVER, fileName, "应用封面", app.getId()) && deleted;
        }
        return deleted;
    }

    private Set<String> resolveCoverFileNames(App app) {
        Set<String> coverFileNames = new HashSet<>();
        String coverFileName = parseLocalCoverFileName(app.getCover());
        if (StrUtil.isNotBlank(coverFileName)) {
            coverFileNames.add(coverFileName);
        }
        if (StrUtil.isNotBlank(app.getCodeGenType())) {
            String generatedCoverFileName = app.getCodeGenType() + "_" + app.getId() + ".png";
            if (isSafeResourceName(generatedCoverFileName)) {
                coverFileNames.add(generatedCoverFileName);
            } else {
                log.warn("跳过非法应用封面文件名，appId={}, fileName={}", app.getId(), generatedCoverFileName);
            }
        }
        return coverFileNames;
    }

    private String parseLocalCoverFileName(String cover) {
        if (StrUtil.isBlank(cover)) {
            return null;
        }
        String coverPath = StrUtil.subBefore(cover, "?", false).trim();
        String outputCoversPath = CaptureConstant.CAPTURE_HOST + "/output_covers/";
        if (!coverPath.startsWith(outputCoversPath)) {
            return null;
        }
        String fileName = coverPath.substring(outputCoversPath.length());
        if (!isSafeResourceName(fileName)) {
            log.warn("跳过非法应用封面文件名，cover={}", cover);
            return null;
        }
        return fileName;
    }

    private boolean deleteDeployResource(App app) {
        if (StrUtil.isBlank(app.getDeployKey())) {
            return true;
        }
        return deleteResource(AppConstant.CODE_DEPLOY_ROOT_DIR, app.getDeployKey(), "应用部署目录", app.getId());
    }

    private boolean deleteResource(String rootPath, String resourceName, String resourceType, Long appId) {
        if (!isSafeResourceName(resourceName)) {
            log.warn("跳过非法{}，appId={}, resourceName={}", resourceType, appId, resourceName);
            return false;
        }
        File resource = new File(rootPath, resourceName);
        if (!resource.exists()) {
            return true;
        }
        boolean deleted = FileUtil.del(resource);
        if (!deleted) {
            log.warn("删除{}失败，appId={}, path={}", resourceType, appId, resource.getAbsolutePath());
        }
        return deleted;
    }

    private boolean isSafeResourceName(String resourceName) {
        return StrUtil.isNotBlank(resourceName)
                && resourceName.equals(new File(resourceName).getName())
                && !resourceName.contains("..")
                && !resourceName.contains("\\");
    }
}
