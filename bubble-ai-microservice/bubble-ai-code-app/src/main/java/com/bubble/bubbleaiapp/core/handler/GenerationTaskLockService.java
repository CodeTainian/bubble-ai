package com.bubble.bubbleaiapp.core.handler;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class GenerationTaskLockService {
    private final RedissonClient redisson;
    public GenerationTaskLockService(RedissonClient redisson) { this.redisson = redisson; }
    public Optional<String> tryAcquire(long appId, String generationId) {
        String token = generationId + ":" + UUID.randomUUID();
        return redisson.<String>getBucket(key(appId)).setIfAbsent(token, Duration.ofHours(2)) ? Optional.of(token) : Optional.empty();
    }
    public void release(long appId, String token) {
        if (token != null) redisson.<String>getBucket(key(appId)).compareAndSet(token, null);
    }
    private String key(long appId) { return "bubble-ai:generation:lock:" + appId; }
}
