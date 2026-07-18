package com.bubble.bubbleai.config;

import com.github.xiaoymin.knife4j.extend.filter.basic.JakartaServletSecurityBasicAuthFilter;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class Knife4jSessionIsolationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(Knife4jAutoConfiguration.class);

    @Test
    void disablingKnife4jPreventsItsSessionCreatingFilterFromBeingRegistered() {
        contextRunner
                .withPropertyValues("knife4j.enable=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(JakartaServletSecurityBasicAuthFilter.class));
    }
}
