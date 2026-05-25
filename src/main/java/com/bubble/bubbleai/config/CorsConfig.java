package com.bubble.bubbleai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置，让整个项目所有的接口‌支持跨域，解决‌跨域报错
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 覆盖所有请求
        registry.addMapping("/**")
                .allowedHeaders("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .exposedHeaders("*")
                // 允许发送 Cookie
                .allowCredentials(true)
                //放行那些域名 必须用patterns，否则*会和allowCredentials 冲突
                .allowedOriginPatterns("*");
    }
}
