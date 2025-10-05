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
import java.nio.channels.Selector;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.event.EventLoop.EventLoopLifeCycle;
import org.traffichunter.titan.core.util.concurrent.AdvancedThreadPoolExecutor;

/**
 * @author yungwang-o
 */
public abstract class AbstractEventLoop extends AdvancedThreadPoolExecutor {

    private final String eventLoopName;

    private final Selector selector;

    private final EventLoopLifeCycle lifeCycle = new EventLoopLifeCycleImpl();

    public AbstractEventLoop(final String eventLoopName) {
        super(AdvancedThreadPoolExecutor.singleThreadExecutor(r -> new Thread(r, eventLoopName)));
        this.eventLoopName = eventLoopName;
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new EventLoopException("Select open error!!", e);
        }
    }

    public int size() {
        return super.getQueue().size();
    }

    public boolean isEmpty() {
        return super.getQueue().isEmpty();
    }

    public boolean inEventLoop(final String eventLoopName) {
        return Thread.currentThread().getName().equals(eventLoopName);
    }

    protected void shutdown(final long timeout, final TimeUnit unit) {

        super.shutdown();
        try {

            if (!super.awaitTermination(timeout, unit)) {
                super.shutdownNow();
            }
        } catch (InterruptedException e) {
            super.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class EventLoopLifeCycleImpl implements EventLoop.EventLoopLifeCycle {

        private EventLoopStatus status;

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
