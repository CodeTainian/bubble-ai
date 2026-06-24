package com.bubble.bubbleai.ai;

import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class AiCodeGenTypeRoutingServiceTest {

    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    @Test
    public void testRoutCodeGenType(){
        String userPrompt = "做一个简单的个人介绍页面";
        CodeGenTypeEnum result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}",userPrompt,result.getValue());
        userPrompt = "做一个公司官网，需要首页、关于我们、联系我们三个页面";
        result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}",userPrompt,result.getValue());
        userPrompt = "帮我生成一个科技服务公司的企业官网，包含首页首屏、服务介绍、核心优势、客户案例、团队介绍和联系表单；整体风格简洁专业，蓝白配色，适合展示 AI 软件定制与数字化转型能力。";
        result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
        log.info("用户需求: {} -> {}",userPrompt,result.getValue());
    }

}