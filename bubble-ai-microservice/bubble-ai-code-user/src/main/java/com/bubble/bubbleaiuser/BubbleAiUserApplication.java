package com.bubble.bubbleaiuser;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.bubble.bubbleaiuser.mapper")
@ComponentScan("com.bubble")
public class BubbleAiUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(BubbleAiUserApplication.class);
    }
}
