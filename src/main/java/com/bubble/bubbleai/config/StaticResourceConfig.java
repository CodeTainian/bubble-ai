package com.bubble.bubbleai.config;

import com.bubble.bubbleai.constant.CaptureConstant;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/output_covers/**")
                .addResourceLocations("file:" + CaptureConstant.CAPTURE_OUTPUT_COVER + "/");
    }
}
