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

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link Worker} backed by one fixed Vert.x {@link Context}.
 *
 * <p>The worker does not close the Vert.x runtime. Its driver owns runtime shutdown; this adapter
 * only rejects submissions after it has been closed.</p>
 *
 * @author yun
 */
final class VertxWorker implements Worker {

    private final Context context;
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Creates a worker that always schedules onto the supplied context. */
    public VertxWorker(Context context) {
        this.context = context;
    }

    @Override
    public void execute(@NonNull Runnable task) {
        if (closed.get()) {
            throw new RejectedExecutionException("Vert.x worker is closed");
        }
        context.runOnContext(ignored -> task.run());
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            execute(() -> {
                try {
                    result.complete(task.call());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    @Override
    public boolean inWorker() {
        return Vertx.currentContext() == context;
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
