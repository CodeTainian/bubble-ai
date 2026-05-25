package com.bubble.bubbleai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.bubble.bubbleai.mapper")
public class BubbleAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BubbleAiApplication.class, args);
    }

}
