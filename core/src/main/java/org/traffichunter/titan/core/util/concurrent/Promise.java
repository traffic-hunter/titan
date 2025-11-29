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
package org.traffichunter.titan.core.util.concurrent;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.event.EventLoop;

/**
 * @author yungwang-o
 */
public interface Promise<C> extends RunnableFuture<C>, Completion<C> {

    static <C> Promise<C> newPromise(EventLoop eventLoop, Runnable task) {
        return new PromiseImpl<>(eventLoop, task);
    }

    static <C> Promise<C> newPromise(EventLoop eventLoop, Callable<C> task) {
        return new PromiseImpl<>(eventLoop, task);
    }

    boolean isSuccess();

    boolean isCancellable();

    @CanIgnoreReturnValue
    Promise<C> addListener(AsyncListener listener);

    @CanIgnoreReturnValue
    Promise<C> addListeners(AsyncListener... listeners);

    @CanIgnoreReturnValue
    Promise<C> removeListener(AsyncListener listener);

    @CanIgnoreReturnValue
    Promise<C> removeListeners(AsyncListener... listeners);

    @CanIgnoreReturnValue
    Promise<C> await() throws InterruptedException;

    @CanIgnoreReturnValue
    Promise<C> await(long timeout, TimeUnit timeUnit) throws InterruptedException;

    Future<C> future();

    boolean isDone();

    Throwable error();
}
