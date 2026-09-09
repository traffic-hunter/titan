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
package org.traffichunter.titan.core.httpserver.threadpool;

import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;
import org.traffichunter.titan.core.util.Pooling;

/**
 * @author yungwang-o
 */
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
    _QUEUED(Pooling._QUEUED) {
        @Override
        public ThreadPool getThreadPool() {
            return new QueuedThreadPool();
        }

        @Override
        public ThreadPool getThreadPool(final int maxThreadPool) {
            return new QueuedThreadPool(maxThreadPool);
        }
    },
    _MONITOR(Pooling._MONITOR) {
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

    public Pooling getPooling() {
        return pooling;
    }

    public JettyThreadPool match(final Pooling pooling) {
        return pooling == this.pooling ? this : null;
    }

    public abstract ThreadPool getThreadPool();

    public abstract ThreadPool getThreadPool(int maxThreadPool);
}
