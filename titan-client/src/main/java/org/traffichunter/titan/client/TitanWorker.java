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
