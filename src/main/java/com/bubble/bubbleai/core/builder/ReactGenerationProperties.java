package com.bubble.bubbleai.core.builder;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bubble-ai.generation.react")
public class ReactGenerationProperties {

    private int installTimeoutSeconds = 300;

    private int buildTimeoutSeconds = 180;

    private int repairAiTimeoutSeconds = 240;

    private int maxRepairAttempts = 3;

    private int maxBuildLogLength = 20_000;

    private boolean stopOnRepeatedError = true;
}
