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
package org.traffichunter.titan.core.httpserver.threadpool;

import lombok.Getter;
import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;
import org.traffichunter.titan.bootstrap.httpserver.Pooling;

/**
 * @author yungwang-o
 */
@Getter
public enum JettyThreadPool {

    _VIRTUAL(Pooling._VIRTUAL) {
        @Override
        public ThreadPool getThreadPool() {
            return new VirtualThreadPool();
        }

        @Override
        public ThreadPool getThreadPool(final int maxThreadPool) {
            return new VirtualThreadPool(maxThreadPool);
        }
    },
    _QUEUED(Pooling.QUEUED) {
        @Override
        public ThreadPool getThreadPool() {
            return new QueuedThreadPool();
        }

        @Override
        public ThreadPool getThreadPool(final int maxThreadPool) {
            return new QueuedThreadPool(maxThreadPool);
        }
    },
    _MONITOR(Pooling.MONITOR) {
        @Override
        public ThreadPool getThreadPool() {
            return new MonitoredQueuedThreadPool();
        }

        @Override
        public ThreadPool getThreadPool(final int maxThreadPool) {
            return new MonitoredQueuedThreadPool(maxThreadPool);
        }
    },
    ;

    private final Pooling pooling;

    JettyThreadPool(final Pooling pooling) {
        this.pooling = pooling;
    }

    public abstract ThreadPool getThreadPool();

    public abstract ThreadPool getThreadPool(int maxThreadPool);
}
