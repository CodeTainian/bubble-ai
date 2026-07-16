package com.bubble.bubbleai.core.handler;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationTaskManagerTest {

    @Test
    void concurrentStartOnlyCreatesOneSourceAndBothCallersJoinIt() throws Exception {
        GenerationTaskManager manager = new GenerationTaskManager();
        AtomicInteger sourceCreations = new AtomicInteger();
        Sinks.Many<String> source = Sinks.many().replay().all();

        var first = manager.start(9L, () -> {
            sourceCreations.incrementAndGet();
            return source.asFlux();
        });
        var second = manager.start(9L, () -> {
            sourceCreations.incrementAndGet();
            return source.asFlux();
        });
        List<String> firstValues = new ArrayList<>();
        List<String> secondValues = new ArrayList<>();
        CountDownLatch subscribersCompleted = new CountDownLatch(2);
        first.subscribe(firstValues::add, ignored -> subscribersCompleted.countDown(), subscribersCompleted::countDown);
        second.subscribe(secondValues::add, ignored -> subscribersCompleted.countDown(), subscribersCompleted::countDown);

        source.tryEmitNext("one");
        source.tryEmitComplete();

        assertTrue(subscribersCompleted.await(5, TimeUnit.SECONDS));
        assertEquals(1, sourceCreations.get());
        assertEquals(List.of("one"), firstValues);
        assertEquals(List.of("one"), secondValues);
        assertTrue(manager.getTaskFlux(9L).isEmpty());
    }
}
