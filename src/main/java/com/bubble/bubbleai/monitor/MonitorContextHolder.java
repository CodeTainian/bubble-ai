package com.bubble.bubbleai.monitor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorContextHolder {

    private static final ThreadLocal<MonitorContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置监控上下文
     */
    public static void setContext(MonitorContext context) {
        if (context == null) {
            CONTEXT_HOLDER.remove();
        } else {
            CONTEXT_HOLDER.set(context.snapshot());
        }
    }

    /**
     * 获取当前监控上下文
     */
    public static MonitorContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除监控上下文
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    public static Scope openScope(MonitorContext context) {
        MonitorContext previous = CONTEXT_HOLDER.get();
        setContext(context);
        return new Scope(previous);
    }

    public static void runWithContext(MonitorContext context, Runnable runnable) {
        try (Scope ignored = openScope(context)) {
            runnable.run();
        }
    }

    public static final class Scope implements AutoCloseable {

        private final MonitorContext previous;
        private boolean closed;

        private Scope(MonitorContext previous) {
            this.previous = previous == null ? null : previous.snapshot();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CONTEXT_HOLDER.remove();
            } else {
                CONTEXT_HOLDER.set(previous);
            }
        }
    }
}
