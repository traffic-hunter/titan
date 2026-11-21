/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

/**
 * Delivers each queue to the subscribers of its destination through a {@link DispatchExporter}.
 *
 * <p>Every started queue gets one long-lived consumer task. The task takes messages off the
 * queue in blocks, hands each block to the exporter and waits for that export before it takes
 * the next one. Consumers are kept per queue instance, so a queue deleted and created again is
 * a new queue with a consumer of its own.</p>
 *
 * <p>As a {@link DispatcherQueueListener} it starts a queue when the path finder creates it and
 * stops it when the path finder removes it.</p>
 *
 * @author yun
 */
final class FanoutDispatcher implements Dispatcher, DispatcherQueueListener {

    private static final long EXPORT_TIMEOUT_SECONDS = 5;
    static final int DEFAULT_MAX_MESSAGE_BLOCK_SIZE = 1;
    static final int DEFAULT_MAX_MESSAGE_BLOCK_BYTES = 16_384;

    private static final Logger log = LoggerFactory.getLogger(FanoutDispatcher.class);

    private final Map<DispatcherQueue, Future<?>> consumers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final DispatchExporter exporter;
    private final int maxMessageBlockSize;
    private final int maxMessageBlockBytes;
    private final AtomicBoolean closed = new AtomicBoolean();

    FanoutDispatcher(ExecutorService executor, DispatchExporter exporter) {
        this(executor, exporter, DEFAULT_MAX_MESSAGE_BLOCK_SIZE, DEFAULT_MAX_MESSAGE_BLOCK_BYTES);
    }

    FanoutDispatcher(
            ExecutorService executor,
            DispatchExporter exporter,
            int maxMessageBlockSize,
            int maxMessageBlockBytes
    ) {
        this.executor = executor;
        this.exporter = exporter;
        this.maxMessageBlockSize = maxMessageBlockSize;
        this.maxMessageBlockBytes = maxMessageBlockBytes;
    }

    @Override
    public void start(DispatcherQueue queue) {
        if (closed.get()) {
            throw new IllegalStateException("Fanout dispatcher is closed");
        }
        if (consumers.containsKey(queue)) {
            return;
        }
        consumers.computeIfAbsent(queue, this::consume);
    }

    @Override
    public void stop(DispatcherQueue queue) {
        Future<?> consumer = consumers.remove(queue);
        if (consumer != null) {
            consumer.cancel(true);
        }
    }

    @Override
    public void queueCreated(DispatcherQueue queue) {
        start(queue);
    }

    @Override
    public void queueRemoved(DispatcherQueue queue) {
        stop(queue);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            consumers.values().forEach(consumer -> consumer.cancel(true));
            consumers.clear();
        }
    }

    private Future<?> consume(DispatcherQueue queue) {
        String group = queue.getGroup();
        Destination destination = queue.route();
        log.info("Starting fanout consumer for group={} destination={}", group, destination.path());

        return executor.submit(() -> {
            try {
                while (!closed.get()
                        && !Thread.currentThread().isInterrupted()
                        && !queue.isClosed()) {

                    final List<Message> block = new ArrayList<>(maxMessageBlockSize);
                    Message message;
                    try {
                        message = queue.dispatch(1, TimeUnit.SECONDS);
                        if (message == null) {
                            continue;
                        }
                        block.add(message);
                        long bytes = message.getSize();

                        while (block.size() < maxMessageBlockSize && bytes < maxMessageBlockBytes) {
                            Message next = queue.dispatch(0, TimeUnit.NANOSECONDS);
                            if (next == null) {
                                break;
                            }

                            block.add(next);
                            bytes += next.getSize();
                        }

                        exporter.export(group, destination, block)
                                .toCompletableFuture()
                                .get(EXPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Throwable e) {
                        // Nothing starts this consumer again, so it has to outlive an Error too.
                        Throwable cause = e;
                        if (e instanceof ExecutionException execution) {
                            Throwable nested = execution.getCause();
                            if (nested != null) {
                                cause = nested;
                            }
                        }
                        if (cause instanceof TimeoutException) {
                            log.warn("Export timed out after {} s. group={}, destination={}",
                                    EXPORT_TIMEOUT_SECONDS,
                                    group, destination.path());
                        } else {
                            log.error("Unexpected error while dispatching message", e);
                        }
                        if (closed.get() || executor.isShutdown()) {
                            break;
                        }
                    } finally {
                        for (Message msg : block) {
                            queue.complete(msg);
                        }
                    }
                }
            } finally {
                consumers.remove(queue);
            }
        });
    }
}
