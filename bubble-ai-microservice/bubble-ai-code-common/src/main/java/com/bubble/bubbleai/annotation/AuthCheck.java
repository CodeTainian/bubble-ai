package com.bubble.bubbleai.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)  // 说明注解只能加在方法上
@Retention(RetentionPolicy.RUNTIME) // 说明注解在运行时仍然可用（可通过反射读取）
public @interface AuthCheck {
    String mustRole() default ""; // 定义一个可选的属性（规则标识），默认值为空字符串
}
