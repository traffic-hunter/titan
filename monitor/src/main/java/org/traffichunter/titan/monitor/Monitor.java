/*
 * The MIT License
 *
 * Copyright (c) 2024 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.monitor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.event.EventManager;
import org.traffichunter.titan.monitor.jmx.JmxMbeanCollector;
import org.traffichunter.titan.monitor.jmx.cpu.CpuData;
import org.traffichunter.titan.monitor.jmx.cpu.JmxCpuMbeanCollector;
import org.traffichunter.titan.monitor.jmx.heap.HeapData;
import org.traffichunter.titan.monitor.jmx.heap.JmxHeapMbeanCollector;
import org.traffichunter.titan.monitor.jmx.thread.JmxThreadMbeanCollector;
import org.traffichunter.titan.monitor.jmx.thread.ThreadData;

/**
 * @author yungwang-o
 */
@Slf4j
public final class Monitor {

    private static final long DEFAULT_SHUTDOWN_TIMEOUT = 10;

    private final Map<Class<?>, JmxMbeanCollector<?>> collectors;

    private final ScheduledExecutorService schedule;

    private final EventManager eventManager = EventManager.INSTANCE;

    private final ScheduleProperties scheduleProperties;

    public Monitor(final ScheduleProperties scheduleProperties, final int scheduledThreadPool) {

        if(scheduledThreadPool == 0) {
            throw new IllegalArgumentException("Scheduled thread pool must be non-zero");
        }

        if(scheduledThreadPool == 1) {
            this.schedule = Executors.newSingleThreadScheduledExecutor(createMonitorThreadFactory());
        } else {
            this.schedule = Executors.newScheduledThreadPool(
                    scheduledThreadPool,
                    createMonitorThreadFactory()
            );
        }

        this.scheduleProperties = Objects.requireNonNull(scheduleProperties, "Schedule properties must not be null");
        this.collectors = Map.of(
                CpuData.class, new JmxCpuMbeanCollector(),
                HeapData.class, new JmxHeapMbeanCollector(),
                ThreadData.class, new JmxThreadMbeanCollector()
        );
    }

    public <R> void doMonitor(final Class<R> dataType) {

        JmxMbeanCollector<R> collector = findMonitor(dataType);

        schedule.scheduleWithFixedDelay(() -> {

                    R collect = collector.collect();

                    if(isAppHealthThresholdExceeded(collect)) {
                        eventManager.post(collect);
                    }
                },
                scheduleProperties.initialDelay(),
                scheduleProperties.delay(),
                scheduleProperties.timeUnit()
        );
    }

    public void close() {

        schedule.shutdown();

        try {
            if (!schedule.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                schedule.shutdownNow();
            }
            log.info("Monitor shutdown completed");
        } catch (InterruptedException e) {
            schedule.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("Monitor shutdown interrupted", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <R> JmxMbeanCollector<R> findMonitor(final Class<R> dataType) {
        JmxMbeanCollector<?> collector = collectors.get(dataType);

        if(collector == null || !dataType.equals(collector.getDataType())) {
            throw new IllegalStateException("Not found collector for type " + dataType.getSimpleName());
        }

        return (JmxMbeanCollector<R>) collector;
    }

    private <R> boolean isAppHealthThresholdExceeded(final R data) {

        switch (data) {
            case CpuData cpuData -> {
                return cpuData.isCheckThreshold(80);
            }
            case HeapData heapData -> {
                return heapData.isCheckThreshold(0.8);
            }
            case ThreadData threadData -> {
                return threadData.isCheckThreshold(100);
            }
            case null, default -> {
                return false;
            }
        }
    }

    private ThreadFactory createMonitorThreadFactory() {
        return run -> {
            Thread thread = new Thread(run, "MonitorThread");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(
                    (thread1, throwable) ->
                            log.info(
                                    "[{}] Monitor thread exception = {}",
                                    thread1.getName(),
                                    throwable.getMessage()
                            )
            );
            return thread;
        };
    }

    @Builder
    public record ScheduleProperties(

            long initialDelay,

            long delay,

            TimeUnit timeUnit
    ) {}
}
