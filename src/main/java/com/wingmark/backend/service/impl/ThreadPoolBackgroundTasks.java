package com.wingmark.backend.service.impl;

import com.wingmark.backend.service.BackgroundTasks;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A small bounded thread pool; when it is full the task runs on the calling thread rather than being dropped. */
@Slf4j
@Component
public class ThreadPoolBackgroundTasks implements BackgroundTasks {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
            runnable -> {
                Thread thread = new Thread(runnable, "background-task");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    @Override
    public void run(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Background task failed", e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
