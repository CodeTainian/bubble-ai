package com.bubble.bubbleai.core.handler;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-instance generation lock. A lease prevents a crashed/restarted service
 * from leaving an application permanently marked as generating.
 */
@Component
public class GenerationTaskLockService {

    private static final Duration LEASE = Duration.ofHours(2);

    private final RedissonClient redissonClient;

    public GenerationTaskLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public Optional<String> tryAcquire(long appId, String generationId) {
        String token = generationId + ":" + UUID.randomUUID();
        RBucket<String> bucket = redissonClient.getBucket(key(appId));
        return bucket.setIfAbsent(token, LEASE) ? Optional.of(token) : Optional.empty();
    }

    public void release(long appId, String token) {
        if (token == null) {
            return;
        }
        redissonClient.<String>getBucket(key(appId)).compareAndSet(token, null);
    }

    private String key(long appId) {
        return "bubble-ai:generation:lock:" + appId;
    }
}
