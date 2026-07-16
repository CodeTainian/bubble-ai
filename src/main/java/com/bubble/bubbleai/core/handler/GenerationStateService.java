package com.bubble.bubbleai.core.handler;

import com.bubble.bubbleai.model.enums.GenerationState;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
public class GenerationStateService {

    private final Cache<Long, GenerationStatus> statuses = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public void transition(long appId, String generationId, GenerationState state, int repairAttempt) {
        statuses.put(appId, new GenerationStatus(generationId, state, repairAttempt, Instant.now()));
        log.info("generation state changed, appId={}, generationId={}, state={}, repairAttempt={}",
                appId, generationId, state, repairAttempt);
    }

    public Optional<GenerationStatus> get(long appId) {
        return Optional.ofNullable(statuses.getIfPresent(appId));
    }

    public record GenerationStatus(
            String generationId,
            GenerationState state,
            int repairAttempt,
            Instant updatedAt
    ) {
    }
}
