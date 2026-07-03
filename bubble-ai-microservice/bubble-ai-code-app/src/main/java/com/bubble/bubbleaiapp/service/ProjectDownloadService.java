package com.bubble.bubbleaiapp.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {


    /**
     * 过滤文件，并下载
     * @param projectPath 项目路径
     * @param downloadFileName 下载的文件名
     * @param response Http响应
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);

}
