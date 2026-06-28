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
package org.traffichunter.titan.fanout;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.HandlerChain;

/**
 * Default forward-only fanout handler chain.
 *
 * @author yun
 */
public class FanoutHandlerChain implements HandlerChain<FanoutContext>, AutoCloseable {

    private final Executor executor;
    private final FanoutHandlerChainImpl head;
    private FanoutHandlerChainImpl tail;

    public FanoutHandlerChain() {
        this(Runnable::run);
    }

    public FanoutHandlerChain(Executor executor) {
        this.executor = executor;
        this.head = this.tail = new FanoutHandlerChainImpl(FanoutHandler.NOOP);
    }

    public FanoutHandlerChain(FanoutHandler... handlers) {
        this(List.of(handlers));
    }

    public FanoutHandlerChain(List<FanoutHandler> handlers) {
        this(Runnable::run, handlers);
    }

    public FanoutHandlerChain(Executor executor, List<FanoutHandler> handlers) {
        this(executor);
        handlers.forEach(this::add);
    }

    public static FanoutHandlerChain chain() {
        return new FanoutHandlerChain();
    }

    public static FanoutHandlerChain chain(Executor executor) {
        return new FanoutHandlerChain(executor);
    }

    @CanIgnoreReturnValue
    public FanoutHandlerChain add(FanoutHandler handler) {
        FanoutHandlerChainImpl context = new FanoutHandlerChainImpl(handler);
        tail.next = context;
        tail = context;
        return this;
    }

    @CanIgnoreReturnValue
    public FanoutHandlerChain addAll(FanoutHandlerChain chain) {
        FanoutHandlerChainImpl context = chain.head.next;
        while (context != null) {
            add(context.handler);
            context = context.next;
        }
        return this;
    }

    @Override
    public CompletableFuture<Void> next(FanoutContext context) {
        return CompletableFuture.supplyAsync(() -> head.next(context), executor)
                .thenCompose(Function.identity());
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        FanoutHandlerChainImpl context = head.next;
        while (context != null) {
            if (context.handler instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            context = context.next;
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static final class FanoutHandlerChainImpl implements HandlerChain<FanoutContext> {

        private final FanoutHandler handler;
        private @Nullable FanoutHandlerChainImpl next;

        private FanoutHandlerChainImpl(FanoutHandler handler) {
            this.handler = handler;
        }

        @Override
        public CompletableFuture<Void> next(FanoutContext context) {
            FanoutHandlerChainImpl chain = next;
            if (chain == null) {
                return CompletableFuture.completedFuture(null);
            }

            return chain.handler.handle(context, chain);
        }
    }
}
