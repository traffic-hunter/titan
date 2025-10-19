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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.concurrent.AdvancedThreadPoolExecutor;
import org.traffichunter.titan.core.util.eventloop.EventLoopConstants;

/**
 * @author yungwang-o
 */
@Slf4j
public class SecondaryNIOEventLoop extends AdvancedThreadPoolExecutor implements EventLoop {

    private final String eventLoopOriginName;

    private final EventLoopLifeCycle lifeCycle = new EventLoopLifeCycleImpl();

    private final Selector selector;

    private final Handler<ChannelContext> readHandler;

    private final Queue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();

    // Optimize atomicReference
    private static final AtomicReferenceFieldUpdater<SecondaryNIOEventLoop, EventLoopStatus> STATUS_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(SecondaryNIOEventLoop.class, EventLoopStatus.class, "status");
    private volatile EventLoopStatus status;

    private static final GlobalShutdownHook shutdownHook = GlobalShutdownHook.INSTANCE;

    public SecondaryNIOEventLoop(
            final Selector selector,
            final int isPendingMaxTasksCapacity,
            final Handler<ChannelContext> readHandler,
            final int eventLoopNameCount
    ) {
        super(AdvancedThreadPoolExecutor.singleThreadExecutor(
                EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME + "-" + eventLoopNameCount,
                isPendingMaxTasksCapacity)
        );
        this.eventLoopOriginName = EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME + "-" + eventLoopNameCount;
        this.selector = selector;
        this.readHandler = readHandler;
        this.status = EventLoopStatus.NOT_INITIALIZED;
        this.status = EventLoopStatus.INITIALIZED;
    }

    @Override
    public void start() {
        Assert.checkState(lifeCycle.isInitialized(), "EventLoop is not initialized");

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
        super.execute(this::start0);
    }

    @Override
    public void restart() {
        Assert.checkState(lifeCycle.isSuspended(), "EventLoop is not suspended");

        log.info("Restarting event loop");
        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
        try {
            super.resume();
        } catch (Exception e) {
            log.error("Failed to resume event loop = {}", e.getMessage());
            shutdown(true, 10, TimeUnit.SECONDS);
        }
    }

    @Override
    public EventLoopLifeCycle getLifeCycle() {
        return lifeCycle;
    }

    @Override
    public void suspend() {
        Assert.checkState(lifeCycle.isStarting(), "EventLoop is not started");

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDING);

        super.pause();

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDED);
    }

    @Override
    public void shutdown(final boolean isGraceful, final long timeout, final TimeUnit unit) {
        Assert.checkState(lifeCycle.isStarting(), "EventLoop is not started");

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

    @Override
    public void close() {
        Assert.checkState(lifeCycle.isStarting(), "EventLoop is not started");

        log.info("Closing event loop");

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPING);

        super.close();

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPED);

        log.info("Closed event loop");
    }

    public void register(final ChannelContext ctx) {
        pendingTasks.offer(() -> doRegister(ctx));
        selector.wakeup();
    }

    public int size() {
        return super.getQueue().size();
    }

    public boolean isEmpty() {
        return super.getQueue().isEmpty();
    }

    public boolean inEvnetLoop() {
        return Thread.currentThread().getName().equals(eventLoopOriginName);
    }

    private void shutdown(final long timeout, final TimeUnit unit) {
        super.shutdown();
        try {

            if (!super.awaitTermination(timeout, unit)) {
                super.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                selector.close();
            } catch (IOException e) {
                log.error("Error closing selector: {}", e.getMessage());
            }
        }
    }

    private void doRegister(final ChannelContext ctx) {
        if(inEvnetLoop()) {
            try {
                log.info("hello");
                ctx.socketChannel().register(selector, SelectionKey.OP_READ, ctx);
                SelectionKey key = ctx.socketChannel().keyFor(selector);
                log.info("post-register key={}, valid={}", key, key != null && key.isValid());
            } catch (IOException e) {
                try {
                    ctx.close();
                } catch (IOException e1) {
                    log.error("Error closing channel = {}", e1.getMessage());
                }
            }
        }
    }

    private void processPendingTasks() {
        Runnable task;

        while ((task = pendingTasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error processing task: {}", e.getMessage());
            }
        }
    }

    private void start0() {
        log.info("Start event loop!!");

        Assert.checkState(selector.isOpen(), "Selector is not open");

        while (lifeCycle.isStarting() && !Thread.currentThread().isInterrupted()) {
            try {
                processPendingTasks();

                int select = selector.select();

                processPendingTasks();

                if (select == 0) {
                    continue;
                }
            } catch (IOException e) {
                Thread.currentThread().interrupt();
                log.error("Failed to select event loop = {}", e.getMessage());
                break;
            }

            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                if(!key.isValid()) {
                    continue;
                }

                if(key.isReadable()) {
                    ChannelContext cc = ChannelContext.select(key);
                    readHandler.handle(cc);
                }
            }
        }
    }

    private class EventLoopLifeCycleImpl implements EventLoop.EventLoopLifeCycle {

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
}