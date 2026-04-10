/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.fanout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.dispatcher.Dispatcher;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.concurrent.Damper;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.concurrent.NoopDamper;
import org.traffichunter.titan.fanout.exporter.FanoutExporter;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractExecutorFanoutGateway implements FanoutGateway {

    private static final Logger log = LoggerFactory.getLogger(AbstractExecutorFanoutGateway.class);

    private final Map<Destination, Future<?>> consumers = new ConcurrentHashMap<>();

    private final ExecutorService executor;
    private final FanoutExporter exporter;
    private final Dispatcher dispatcher;
    private final AtomicBoolean isClosed = new AtomicBoolean();
    private final Damper damper;

    protected AbstractExecutorFanoutGateway(
            ExecutorService executor,
            FanoutExporter exporter,
            Dispatcher dispatcher
    ) {
        this(executor, exporter, dispatcher, NoopDamper.getInstance());
    }

    protected AbstractExecutorFanoutGateway(
            ExecutorService executor,
            FanoutExporter exporter,
            Dispatcher dispatcher,
            Damper damper
    ) {
        this.executor = Assert.checkNotNull(executor, "executor");
        this.exporter = Assert.checkNotNull(exporter, "exporter");
        this.dispatcher = Assert.checkNotNull(dispatcher, "dispatcher");
        this.damper = Assert.checkNotNull(damper, "damper");
    }

    @Override
    public Future<Void> fanout(Collection<Destination> destinations) {
        Assert.checkNotNull(destinations, "destinations");

        if (isClosed.get()) {
            throw new IllegalStateException("FanoutGateway is closed");
        }

        CompletableFuture<?>[] futures = (CompletableFuture<?>[]) destinations.stream()
                .map(this::fanout)
                .toArray(Future[]::new);

        return CompletableFuture.allOf(futures);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<Void> fanout(Destination destination) {
        Assert.checkNotNull(destination, "destination");

        if (isClosed.get()) {
            throw new IllegalStateException("FanoutGateway is closed");
        }

        return (Future<Void>) consumers.computeIfAbsent(destination, this::consume);
    }

    @Override
    public boolean isOpen() {
        return !isClosed.get();
    }

    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            consumers.values().forEach(future -> future.cancel(true));
            consumers.clear();
            executor.shutdown();
        }
    }

    private CompletableFuture<Void> consume(Destination destination) {
        DispatcherQueue dispatcherQueue = dispatcher.find(destination);
        if (dispatcherQueue == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Dispatcher queue not found for destination: " + destination)
            );
        }

        return CompletableFuture.runAsync(() -> {
            try {
                while (!isClosed() && !Thread.currentThread().isInterrupted()) {
                    damper.acquire();
                    try {
                        Message message = dispatcherQueue.dispatch();
                        if (message == null) {
                            break;
                        }

                        exporter.send(destination, message);
                    } catch (InterruptedException e) {
                        log.error("Interrupted while waiting for message to be delivered", e);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Unexpected error while dispatching message", e);
                        if (isClosed() || executor.isShutdown()) {
                            break;
                        }
                    } finally {
                        damper.release();
                    }
                }
            } finally {
                consumers.remove(destination);
            }
        }, executor);
    }
}
