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

import org.traffichunter.titan.dispatch.exporter.DispatchExporter;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Dispatch gateway backed by a fixed-size platform thread pool.
 *
 * <p>This mode is useful when dispatch work should be bounded by a small number
 * of OS threads. It limits executor parallelism directly through the pool size.</p>
 *
 * @author yun
 */
@Deprecated(since = "0.9.0")
class ThreadPoolExecutorDispatchGateway extends AbstractExecutorDispatchGateway {

    public ThreadPoolExecutorDispatchGateway(DispatchExporter exporter) {
        this(exporter, Dispatcher.getDefault());
    }

    public ThreadPoolExecutorDispatchGateway(DispatchExporter exporter, Dispatcher dispatcher) {
        this(Runtime.getRuntime().availableProcessors() * 2, exporter, dispatcher);
    }

    public ThreadPoolExecutorDispatchGateway(
            int nThreads,
            DispatchExporter exporter,
            Dispatcher dispatcher
    ) {
        super(
                Executors.newFixedThreadPool(nThreads, newThreadFactory()),
                exporter,
                dispatcher
        );
    }

    private static ThreadFactory newThreadFactory() {
        return Thread.ofPlatform()
                .name("FanoutThread")
                .factory();
    }
}
