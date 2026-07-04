package com.bubble.bubbleaiuser;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.bubble.bubbleaiuser.mapper")
@ComponentScan("com.bubble")
@EnableDubbo
public class BubbleAiUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(BubbleAiUserApplication.class);
    }
}
