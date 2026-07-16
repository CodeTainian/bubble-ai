package com.bubble.bubbleaiapp.core.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Keeps code generation running after the browser SSE connection is closed.
 */
@Slf4j
@Component
public class GenerationTaskManager {

    private final ConcurrentMap<Long, GenerationTask> taskMap = new ConcurrentHashMap<>();

    public Flux<String> start(Long appId, Flux<String> sourceFlux) {
        return start(appId, () -> sourceFlux);
    }

    public Flux<String> start(Long appId, Supplier<Flux<String>> sourceSupplier) {
        GenerationTask newTask = new GenerationTask(appId);
        GenerationTask existingTask = taskMap.putIfAbsent(appId, newTask);
        if (existingTask != null) {
            return existingTask.asFlux();
        }
        Flux<String> sourceFlux;
        try {
            sourceFlux = sourceSupplier.get();
        } catch (Throwable error) {
            taskMap.remove(appId, newTask);
            newTask.emitError(error);
            return newTask.asFlux();
        }
        sourceFlux
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signalType -> taskMap.remove(appId, newTask))
                .subscribe(
                        newTask::emitNext,
                        newTask::emitError,
                        newTask::emitComplete
                );
        return newTask.asFlux();
    }

    public Optional<Flux<String>> getTaskFlux(Long appId) {
        return Optional.ofNullable(taskMap.get(appId)).map(GenerationTask::asFlux);
    }

    public boolean isRunning(Long appId) {
        return taskMap.containsKey(appId);
    }

    private static final class GenerationTask {

        private final Long appId;

        private final Sinks.Many<String> sink = Sinks.many().replay().all();

        private GenerationTask(Long appId) {
            this.appId = appId;
        }

        private Flux<String> asFlux() {
            return sink.asFlux();
        }

        private void emitNext(String chunk) {
            Sinks.EmitResult result = sink.tryEmitNext(chunk);
            if (result.isFailure()) {
                log.debug("drop generation chunk, appId={}, result={}", appId, result);
            }
        }

        private void emitError(Throwable error) {
            Sinks.EmitResult result = sink.tryEmitError(error);
            if (result.isFailure()) {
                log.debug("drop generation error, appId={}, result={}", appId, result, error);
            }
        }

        private void emitComplete() {
            Sinks.EmitResult result = sink.tryEmitComplete();
            if (result.isFailure()) {
                log.debug("drop generation complete signal, appId={}, result={}", appId, result);
            }
        }
    }
}
