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
package org.traffichunter.titan.client;

import org.jspecify.annotations.NonNull;
import org.traffichunter.titan.core.channel.EventLoop;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * {@link Worker} backed by one Titan {@link EventLoop}.
 *
 * <p>Client state callbacks and native scheduled tasks run on the selected event loop.
 * This adapter converts Titan promises to {@link CompletableFuture} for the client.</p>
 *
 * @author yun
 */
final class TitanWorker implements Worker {

    private final EventLoop worker;

    /** Creates a worker around the fixed event loop selected by the driver. */
    public TitanWorker(EventLoop worker) {
        this.worker = worker;
    }

    @Override
    public void execute(@NonNull Runnable task) {
        worker.execute(task);
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return worker.submit(task).toCompletableFuture();
    }

    @Override
    public boolean inWorker() {
        return worker.inEventLoop();
    }

    @Override
    public void close() throws Exception {
        worker.close();
    }
}
