/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
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
package org.traffichunter.titan.core.event;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.event.NIOEventLoop.EventLoopLifeCycleImpl.EventLoopStatus;
import org.traffichunter.titan.core.transport.InetServer.ServerException;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;
import org.traffichunter.titan.core.util.concurrent.AdvancedThreadPoolExecutor;

/**
 * A high-performance, single-threaded NIO-based event loop implementation that provides
 * efficient asynchronous I/O operations using Java NIO's {@link Selector} mechanism.
 *
 * <p>This implementation follows the reactor pattern and is designed to handle high-throughput,
 * low-latency network operations in a non-blocking manner. The event loop processes three
 * primary types of I/O events:</p>
 *
 * <ul>
 *   <li><strong>Accept Events</strong>: Handles incoming client connection requests</li>
 *   <li><strong>Read Events</strong>: Processes data received from connected clients</li>
 *   <li><strong>Write Events</strong>: Manages data transmission to connected clients</li>
 * </ul>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Single-threaded event processing for optimal performance</li>
 *   <li>Thread-safe state management using atomic operations</li>
 *   <li>Graceful shutdown with configurable timeout</li>
 *   <li>Runtime suspension and resumption capabilities</li>
 *   <li>Bounded task queue with configurable capacity</li>
 * </ul>
 *
 * <h3>Lifecycle Management:</h3>
 * <p>The event loop follows a well-defined state transition model:</p>
 * <pre>
 * NOT_INITIALIZED → INITIALIZED → STARTING → (SUSPENDING ⇄ SUSPENDED) → STOPPING → STOPPED
 * </pre>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Create and configure the event loop
 * EventLoop eventLoop = new NIOEventLoop.SingleEventLoopBuilderImpl()
 *     .selector(Selector.open())
 *     .capacity(1000)
 *     .onAccept(this::handleAccept)
 *     .onRead(this::handleRead)
 *     .onWrite(this::handleWrite)
 *     .build();
 *
 * // Start processing events
 * eventLoop.start();
 *
 * // Graceful shutdown with 30-second timeout
 * eventLoop.shutdown(true, 30, TimeUnit.SECONDS);
 * }</pre>
 *
 * <h3>Performance Considerations:</h3>
 * <p>This implementation is optimized for high-performance scenarios and includes
 * several performance enhancements:</p>
 * <ul>
 *   <li>Uses {@link AtomicReferenceFieldUpdater} for lock-free state transitions</li>
 *   <li>Employs a dedicated worker thread with optimized thread pool configuration</li>
 *   <li>Implements efficient memory management with try-with-resources patterns</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. All state transitions
 * are performed atomically using {@link AtomicReferenceFieldUpdater}, ensuring
 * consistent state management across concurrent operations.</p>
 *
 * <p><strong>Important:</strong> Since the event loop operates on a single thread,
 * blocking operations within event handlers will stall the entire event processing
 * pipeline. All I/O handlers must be non-blocking and complete quickly.</p>
 *
 * @author yungwang-o
 * @since 1.0
 * @see EventLoop
 * @see Selector
 * @see ChannelContext
 * @see AdvancedThreadPoolExecutor
 */
@Slf4j
final class NIOEventLoop implements EventLoop {

    private static final String EVENT_LOOP_THREAD_NAME = "EventLoopWorkerThread";

    private final AdvancedThreadPoolExecutor eventLoopExecutor;

    private final EventLoopLifeCycle lifeCycle = new EventLoopLifeCycleImpl();

    private final Selector selector;

    private final Handler<ChannelContext> readHandler;

    private final Handler<ChannelContext> writeHandler;

    private final Handler<Selector> acceptHandler;

    // Optimize atomicReference
    private static final AtomicReferenceFieldUpdater<NIOEventLoop, EventLoopStatus> STATUS_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(NIOEventLoop.class, EventLoopStatus.class, "status");
    private volatile EventLoopStatus status;

    private static final GlobalShutdownHook shutdownHook = GlobalShutdownHook.INSTANCE;

    private NIOEventLoop(
        final Selector selector,
        final int isPendingMaxTasksCapacity,
        final Handler<ChannelContext> readHandler,
        final Handler<ChannelContext> writeHandler,
        final Handler<Selector> acceptHandler
    ) {
        if(isPendingMaxTasksCapacity <= 0) {
            throw new IllegalArgumentException("Task pending max tasks capacity must be greater than zero");
        }

        this.selector = selector;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.acceptHandler = acceptHandler;
        this.status = EventLoopStatus.NOT_INITIALIZED;
        this.eventLoopExecutor = Objects.requireNonNull(
                initializeThreadPoolExecutors(isPendingMaxTasksCapacity),
                "initializeThreadPoolExecutors"
        );
        this.eventLoopExecutor.allowCoreThreadTimeOut(false);
        if(!this.eventLoopExecutor.prestartCoreThread()) {
            throw new EventLoopException("EventLoop all core threads have already been started");
        }
        this.status = EventLoopStatus.INITIALIZED;
    }

    @Override
    public void start() {
        if(lifeCycle.isInitialized()) {
            STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
            eventLoopExecutor.execute(this::start0);
        } else {
            throw new EventLoopException("EventLoop is not initialized");
        }
    }

    @Override
    @ThreadSafe
    public void restart() {
        if(lifeCycle.isSuspended()) {
            log.info("Restarting event loop");
            STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
            eventLoopExecutor.resume();
        } else {
            throw new EventLoopException("EventLoop is not suspended");
        }
    }

    @Override
    public EventLoopLifeCycle getLifeCycle() {
        return lifeCycle;
    }

    public int size() {
        return eventLoopExecutor.getQueue().size();
    }

    public boolean isEmpty() {
        return eventLoopExecutor.getQueue().isEmpty();
    }

    public boolean inEvnetLoop() {
        return Thread.currentThread().getName().equals(EVENT_LOOP_THREAD_NAME);
    }

    @Override
    @ThreadSafe
    public void suspend() {

        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDING);

        eventLoopExecutor.pause();

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDED);
    }

    @Override
    @ThreadSafe
    public void shutdown(final boolean isGraceful, final long timeout, final TimeUnit unit) {

        if(!lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPING);

        if(isGraceful) {
            log.info("is graceful shutdown");
            shutdownHook.addShutdownCallback(() -> shutdown(timeout, unit));
        } else {
            log.info("shutdown");
            shutdown(timeout, unit);
        }

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPED);
    }

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are executed,
     * but no new tasks will be accepted. waiting 1day
     */
    @Override
    public void close() {
        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        log.info("Closing event loop");

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPING);

        eventLoopExecutor.close();

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPED);

        log.info("Closed event loop");
    }

    @ThreadSafe
    private void shutdown(final long timeout, final TimeUnit unit) {

        eventLoopExecutor.shutdown();
        try {

            if (!eventLoopExecutor.awaitTermination(timeout, unit)) {
                eventLoopExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.info("interrupted while shutting down");
            eventLoopExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static AdvancedThreadPoolExecutor initializeThreadPoolExecutors(final int isPendingMaxTasksCapacity) {
        return new AdvancedThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(isPendingMaxTasksCapacity),
                (r) -> new Thread(r, EVENT_LOOP_THREAD_NAME)
        );
    }

    private void start0() {

        log.info("Start event loop!!");

        if(!selector.isOpen()) {
            throw new IllegalStateException("Failed event loop; not open selector");
        }

        while (lifeCycle.isStarting() && !Thread.currentThread().isInterrupted()) {
            try {
                int select = selector.select();
                if (select == 0) {
                    continue;
                }
            } catch (IOException e) {
                Thread.currentThread().interrupt();
                throw new ServerException(e);
            }

            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                if(!key.isValid()) {
                    continue;
                }

                if(key.isAcceptable()) {
                    acceptHandler.handle(selector);
                } else if(key.isReadable()) {
                    try (ChannelContext cc = ChannelContext.select(key)) {
                        readHandler.handle(cc);
                    } catch (IOException e) {
                        key.cancel();
                        throw new ServerException("Failed readable", e);
                    }
                } else if(key.isWritable()) {
                    try (ChannelContext cc = ChannelContext.select(key)) {
                       writeHandler.handle(cc);
                    } catch (IOException e) {
                        key.cancel();
                        throw new ServerException("Failed writable", e);
                    }
                }
            }
        }
    }

    static class SingleEventLoopBuilderImpl implements EventLoop.Builder {

        private Selector selector;
        private int capacity;
        private Handler<ChannelContext> readHandler;
        private Handler<ChannelContext> writeHandler;
        private Handler<Selector> acceptHandler;

        @Override
        public Builder selector(final Selector selector) {
            Objects.requireNonNull(selector, "selector");
            this.selector = selector;
            return this;
        }

        @Override
        public Builder capacity(final int capacity) {
            this.capacity = capacity;
            return this;
        }

        @Override
        public Builder onRead(final Handler<ChannelContext> handler) {
            Objects.requireNonNull(handler, "on read handler");
            this.readHandler = handler;
            return this;
        }

        @Override
        public Builder onWrite(final Handler<ChannelContext> handler) {
            Objects.requireNonNull(handler, "on write handler");
            this.writeHandler = handler;
            return this;
        }

        @Override
        public Builder onAccept(final Handler<Selector> handler) {
            Objects.requireNonNull(handler, "on accept handler");
            this.acceptHandler = handler;
            return this;
        }

        @Override
        public EventLoop build() {
            return new NIOEventLoop(selector, capacity, readHandler, writeHandler, acceptHandler);
        }
    }

    class EventLoopLifeCycleImpl implements EventLoop.EventLoopLifeCycle {

        enum EventLoopStatus {
            NOT_INITIALIZED, INITIALIZED, SUSPENDING, SUSPENDED, STARTING, STOPPING, STOPPED;
        }

        @Override
        public boolean isNotInitialized() {
            return EventLoopStatus.NOT_INITIALIZED == status;
        }

        @Override
        public boolean isSuspending() {
            return EventLoopStatus.SUSPENDING == status;
        }

        @Override
        public boolean isSuspended() {
            return EventLoopStatus.SUSPENDED == status;
        }

        @Override
        public boolean isInitialized() {
            return EventLoopStatus.INITIALIZED == status;
        }

        @Override
        public boolean isStarting() {
            return EventLoopStatus.STARTING == status;
        }

        @Override
        public boolean isStopping() {
            return EventLoopStatus.STOPPING == status;
        }

        @Override
        public boolean isStopped() {
            return EventLoopStatus.STOPPED == status;
        }
    }

    private static class EventLoopException extends RuntimeException {

        public EventLoopException() {}

        public EventLoopException(final String message) {
            super(message);
        }

        public EventLoopException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public EventLoopException(final Throwable cause) {
            super(cause);
        }

        public EventLoopException(final String message,
                                  final Throwable cause,
                                  final boolean enableSuppression,
                                  final boolean writableStackTrace) {

            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}