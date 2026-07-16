package com.bubble.bubbleaiapp.core.handler;

import com.bubble.bubbleai.model.enums.GenerationState;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Slf4j @Component
public class GenerationStateService {
    private final Cache<Long, GenerationState> states = Caffeine.newBuilder().maximumSize(10000)
            .expireAfterWrite(Duration.ofHours(1)).build();
    public void transition(long appId, String generationId, GenerationState state, int attempt) {
        states.put(appId, state);
        log.info("generation state changed, appId={}, generationId={}, state={}, repairAttempt={}", appId, generationId, state, attempt);
    }
}
