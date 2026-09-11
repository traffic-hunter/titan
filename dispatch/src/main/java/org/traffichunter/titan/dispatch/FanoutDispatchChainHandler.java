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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

/**
 * Owns destination consumer registration, execution, and removal after messages are routed.
 *
 * <p>At most one long-lived consumer is registered per destination within a group. The same
 * destination in two groups is two queues and therefore two consumers. The handler starts that task
 * without awaiting its completion because dispatch completion only represents successful
 * queue admission and consumer activation. Queue deletion and handler shutdown cancel registered
 * consumers and let their polling loops observe the corresponding lifecycle state.</p>
 *
 * @author yun
 */
final class FanoutDispatchChainHandler implements DispatchChainHandler {

    private static final Logger log = LoggerFactory.getLogger(FanoutDispatchChainHandler.class);

    private final Map<ConsumerKey, CompletableFuture<@Nullable Void>> consumers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final DispatchExporter exporter;
    private final Dispatcher dispatcher;
    private final AtomicBoolean closed = new AtomicBoolean();

    FanoutDispatchChainHandler(
            ExecutorService executor,
            DispatchExporter exporter,
            Dispatcher dispatcher
    ) {
        this.executor = executor;
        this.exporter = exporter;
        this.dispatcher = dispatcher;
    }

    @Override
    public DispatchChain handle(DispatchContext context, DispatchChain chain) {
        Message message = context.getMessage();
        fanout(message.getGroup(), message.getDestination());
        return chain.next(context);
    }

    CompletableFuture<@Nullable Void> fanout(String group, Destination destination) {
        if (closed.get()) {
            throw new IllegalStateException("Fanout dispatch handler is closed");
        }
        return consumers.computeIfAbsent(new ConsumerKey(group, destination), this::consume);
    }

    DispatcherQueueDeleteResult deleteQueue(Destination destination, boolean force) {
        if (closed.get()) {
            throw new IllegalStateException("Fanout dispatch handler is closed");
        }

        DispatcherQueue queue = dispatcher.get(destination);
        if (queue == null) {
            return DispatcherQueueDeleteResult.notFound();
        }
        int size = queue.size();
        if (size > 0 && !force) {
            return DispatcherQueueDeleteResult.notEmpty(size);
        }

        // Remove the queue this call looked up, never a replacement created since. The
        // dispatcher unregisters the MBean of whatever it actually removed.
        if (!dispatcher.remove(queue)) {
            return DispatcherQueueDeleteResult.notFound();
        }
        queue.close();
        if (force) {
            queue.clear();
        }

        CompletableFuture<@Nullable Void> consumer =
                consumers.remove(new ConsumerKey(DestinationGroups.DEFAULT, destination));
        if (consumer != null) {
            consumer.cancel(true);
        }
        return DispatcherQueueDeleteResult.deleted(size);
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            consumers.values().forEach(future -> future.cancel(true));
            consumers.clear();
        }
    }

    private CompletableFuture<@Nullable Void> consume(ConsumerKey key) {
        DispatcherQueue queue = dispatcher.getOrPut(key.group(), key.destination());
        log.info("Starting fanout consumer for group={} destination={}", key.group(), key.destination().path());

        CompletableFuture<@Nullable Void> result = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                while (!closed.get()
                        && !Thread.currentThread().isInterrupted()
                        && !queue.isClosed()) {
                    try {
                        Message message = queue.dispatch(1, TimeUnit.SECONDS);
                        if (message == null) {
                            continue;
                        }
                        exporter.export(key.group(), key.destination(), message);
                    } catch (InterruptedException e) {
                        log.error("Interrupted while waiting for message to be delivered", e);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Unexpected error while dispatching message", e);
                        if (closed.get() || executor.isShutdown()) {
                            break;
                        }
                    }
                }
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            } finally {
                consumers.remove(key, result);
            }
        });
        return result;
    }

    /** Queue identity as seen by fanout: a destination inside one group. */
    private record ConsumerKey(String group, Destination destination) {
    }
}
