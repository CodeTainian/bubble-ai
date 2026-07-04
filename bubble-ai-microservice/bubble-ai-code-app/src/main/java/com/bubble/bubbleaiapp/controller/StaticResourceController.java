package com.bubble.bubbleaiapp.controller;


import com.bubble.bubbleai.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/static")
public class StaticResourceController {
    private static final Path PREVIEW_ROOT_DIR = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR).toAbsolutePath().normalize();
    private static final Path DEPLOY_ROOT_DIR = Paths.get(AppConstant.CODE_DEPLOY_ROOT_DIR).toAbsolutePath().normalize();

    /**
     * 提供预览/部署静态资源访问，支持目录重定向。
     * 访问格式：<a href="http://localhost:8125/api/static/">...</a>{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的 URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            Path filePath = resolveResourcePath(deployKey, resourcePath);
            // 检查文件是否存在
            if (filePath == null) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath.toString()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Path resolveResourcePath(String deployKey, String resourcePath) {
        String relativeResourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        Path deployFilePath = resolveUnderRoot(DEPLOY_ROOT_DIR, deployKey, relativeResourcePath);
        if (isReadableFile(deployFilePath)) {
            return deployFilePath;
        }
        Path previewFilePath = resolveUnderRoot(PREVIEW_ROOT_DIR, deployKey, relativeResourcePath);
        if (isReadableFile(previewFilePath)) {
            return previewFilePath;
        }
        return null;
    }

    private Path resolveUnderRoot(Path rootDir, String deployKey, String relativeResourcePath) {
        Path filePath = rootDir.resolve(deployKey).resolve(relativeResourcePath).normalize();
        return filePath.startsWith(rootDir) ? filePath : null;
    }

    private boolean isReadableFile(Path filePath) {
        return filePath != null && Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type。
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }

}
