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
 * Dispatch gateway backed by one virtual thread per submitted task.
 *
 * <p>Destination consumers are naturally long-lived and often block while
 * waiting for dispatcher queues. Virtual threads make that blocking cheap while
 * each destination consumer still exports messages sequentially.</p>
 *
 * @author yun
 */
class VirtualThreadExecutorDispatchGateway extends AbstractExecutorDispatchGateway {

    public VirtualThreadExecutorDispatchGateway(DispatchExporter exporter) {
        this(exporter, Dispatcher.getDefault());
    }

    public VirtualThreadExecutorDispatchGateway(
            DispatchExporter exporter,
            Dispatcher dispatcher
    ) {
        super(
                Executors.newThreadPerTaskExecutor(newThreadFactory()),
                exporter,
                dispatcher
        );
    }

    private static ThreadFactory newThreadFactory() {
        return Thread.ofVirtual()
                .name("DispatchVirtualThread")
                .factory();
    }
}
