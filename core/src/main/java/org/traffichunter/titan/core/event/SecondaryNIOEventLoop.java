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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.channel.Context;
import org.traffichunter.titan.core.util.concurrent.AdvancedThreadPoolExecutor;

/**
 * @author yungwang-o
 */
@Slf4j
public class SecondaryNIOEventLoop extends AdvancedThreadPoolExecutor implements EventLoop {

    private static final String EVENT_LOOP_THREAD_NAME = "SecondaryEventLoopWorkerThread";

    private final EventLoopLifeCycle lifeCycle = new EventLoopLifeCycleImpl();

    private final Selector selector;

    private final Handler<ChannelContext> readHandler;

    private final Handler<ChannelContext> writeHandler;

    // Optimize atomicReference
    private static final AtomicReferenceFieldUpdater<SecondaryNIOEventLoop, EventLoopStatus> STATUS_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(SecondaryNIOEventLoop.class, EventLoopStatus.class, "status");
    private volatile EventLoopStatus status;

    private static final GlobalShutdownHook shutdownHook = GlobalShutdownHook.INSTANCE;

    public SecondaryNIOEventLoop(
            final Selector selector,
            final int isPendingMaxTasksCapacity,
            final Handler<ChannelContext> readHandler,
            final Handler<ChannelContext> writeHandler
    ) {
        super(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(isPendingMaxTasksCapacity),
                (r) -> new Thread(r, EVENT_LOOP_THREAD_NAME)
        );
        this.selector = selector;
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
        this.status = EventLoopStatus.NOT_INITIALIZED;
        super.allowCoreThreadTimeOut(false);
        if(!super.prestartCoreThread()) {
            throw new EventLoopException("EventLoop all core threads have already been started");
        }
        this.status = EventLoopStatus.INITIALIZED;
    }

    @Override
    public void start() {
        if(lifeCycle.isInitialized()) {
            STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
            super.execute(this::start0);
        } else {
            throw new EventLoopException("EventLoop is not initialized");
        }
    }

    @Override
    public void restart() {
        if(lifeCycle.isSuspended()) {
            log.info("Restarting event loop");
            STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STARTING);
            super.resume();
        } else {
            throw new EventLoopException("EventLoop is not suspended");
        }
    }

    @Override
    public EventLoopLifeCycle getLifeCycle() {
        return lifeCycle;
    }

    @Override
    public void suspend() {

        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDING);

        super.pause();

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.SUSPENDED);
    }

    @Override
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

    @Override
    public void close() {

        if(lifeCycle.isStarting()) {
            throw new EventLoopException("EventLoop is not started");
        }

        log.info("Closing event loop");

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPING);

        super.close();

        STATUS_UPDATER.compareAndSet(this, status, EventLoopStatus.STOPPED);

        log.info("Closed event loop");
    }

    public void register(final ChannelContext ctx) {
        selector.wakeup();

        try {
            ctx.socketChannel().register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, ctx);
        } catch (IOException e) {
            try {
                ctx.close();
            } catch (IOException e1) {
                log.error("Error closing channel = {}", e1.getMessage());
            }
        }
    }

    public int size() {
        return super.getQueue().size();
    }

    public boolean isEmpty() {
        return super.getQueue().isEmpty();
    }

    public boolean inEvnetLoop() {
        return Thread.currentThread().getName().equals(EVENT_LOOP_THREAD_NAME);
    }

    private void shutdown(final long timeout, final TimeUnit unit) {

        super.shutdown();
        try {

            if (!super.awaitTermination(timeout, unit)) {
                super.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.info("interrupted while shutting down");
            super.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
                } else if(key.isWritable()) {
                    ChannelContext cc = ChannelContext.select(key);
                    writeHandler.handle(cc);
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