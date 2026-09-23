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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

/**
 * Owns destination consumer registration, execution, and removal after messages are routed.
 *
 * <p>At most one long-lived consumer is registered per destination within a group. The same
 * destination in two groups is two queues and therefore two consumers, and a queue deleted and
 * created again is a new queue that gets a consumer of its own. The handler starts that task
 * without awaiting its completion because dispatch completion only represents successful
 * queue admission and consumer activation. Queue deletion and handler shutdown cancel registered
 * consumers and let their polling loops observe the corresponding lifecycle state.</p>
 *
 * @author yun
 */
final class FanoutDispatchChainHandler implements DispatchChainHandler {

    private static final Logger log = LoggerFactory.getLogger(FanoutDispatchChainHandler.class);

    private final Map<ConsumerKey, Consumer> consumers = new ConcurrentHashMap<>();
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

        ConsumerKey key = new ConsumerKey(group, destination);
        Consumer existing = consumers.get(key);
        if (existing != null && !existing.queue().isClosed()) {
            return existing.task();
        }

        // The registered consumer drains a queue that has since been deleted, and it only ever
        // drains the instance it was handed. Whatever queue the message just went into is a
        // different one and needs a consumer of its own, or it would sit there undelivered.
        return consumers.compute(key, (ignored, current) -> {
            if (current != null && !current.queue().isClosed()) {
                return current;
            }
            return consume(key);
        }).task();
    }

    /**
     * Stops the consumer of this very queue, if it is the one registered.
     *
     * <p>A replacement queue registers a consumer of its own, and taking that one down would
     * leave the new queue with nothing draining it. Call this while the queue is still open and
     * registered, so the consumer found here is the queue's own.</p>
     */
    void detach(DispatcherQueue queue) {
        ConsumerKey key = new ConsumerKey(queue.getGroup(), queue.route());
        Consumer consumer = consumers.get(key);
        if (consumer != null && consumer.queue() == queue) {
            consumers.remove(key, consumer);
            consumer.cancel();
        }
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            consumers.values().forEach(Consumer::cancel);
            consumers.clear();
        }
    }

    private Consumer consume(ConsumerKey key) {
        DispatcherQueue queue = dispatcher.get(key.group(), key.destination());
        if (queue == null) {
            throw new IllegalStateException("Queue for " + key + " was removed before its consumer started");
        }
        log.info("Starting fanout consumer for group={} destination={}", key.group(), key.destination().path());

        CompletableFuture<@Nullable Void> result = new CompletableFuture<>();
        Future<?> handle = executor.submit(() -> {
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
                consumers.computeIfPresent(key, (ignored, current) ->
                        current.queue() == queue ? null : current);
            }
        });
        return new Consumer(queue, result, handle);
    }

    /** Queue identity as seen by fanout: a destination inside one group. */
    private record ConsumerKey(String group, Destination destination) {

        @Override
        public String toString() {
            return group + ":" + destination;
        }
    }

    /**
     * A running consumer together with the queue instance it drains.
     *
     * <p>The queue is what makes a consumer replaceable. A key outlives the queue it named, so
     * without the instance neither a delete nor a later message could tell a consumer that is
     * still serving the current queue from one left over from a deleted queue.</p>
     */
    private record Consumer(
            DispatcherQueue queue,
            CompletableFuture<@Nullable Void> task,
            Future<?> handle
    ) {

        /** Interrupts the parked thread. The executor runs the handle, not {@link #task()}. */
        void cancel() {
            handle.cancel(true);
            task.cancel(true);
        }
    }
}
