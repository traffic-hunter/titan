/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
