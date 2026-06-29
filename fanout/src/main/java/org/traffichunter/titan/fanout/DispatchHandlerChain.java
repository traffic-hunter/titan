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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.channel.chain.AbstractHandlerChain;
import org.traffichunter.titan.core.util.channel.chain.LinkedHandlerChainNode;

/**
 * Spark-style handler chain for dispatch work.
 *
 * <p>The dispatch chain starts with routing and can be extended with handlers
 * such as backup, metrics, or validation. Handlers continue propagation by
 * calling {@link org.traffichunter.titan.core.util.channel.chain.HandlerChain#sparkChainHandler(Object)}
 * on the supplied chain context.</p>
 *
 * @author yun
 */
public class DispatchHandlerChain extends AbstractHandlerChain<DispatchHandlerChain.Node, DispatchContext> {

    private final Executor executor;

    public static DispatchHandlerChain chain() {
        return new DispatchHandlerChain(Runnable::run);
    }

    public static DispatchHandlerChain chain(Executor executor) {
        return new DispatchHandlerChain(executor);
    }

    public DispatchHandlerChain() {
        this(Runnable::run);
    }

    public DispatchHandlerChain(Executor executor) {
        super(new Node(DispatchChainHandler.NOOP));
        this.executor = executor;
    }

    public DispatchHandlerChain(DispatchChainHandler... handlers) {
        this(List.of(handlers));
    }

    public DispatchHandlerChain(List<DispatchChainHandler> handlers) {
        this(Runnable::run, handlers);
    }

    public DispatchHandlerChain(Executor executor, List<DispatchChainHandler> handlers) {
        this(executor);
        addAll(handlers);
    }

    @CanIgnoreReturnValue
    public DispatchHandlerChain add(DispatchChainHandler handler) {
        return addLast(handler);
    }

    @CanIgnoreReturnValue
    public DispatchHandlerChain addFirst(DispatchChainHandler handler) {
        addFirst(new Node(handler));
        return this;
    }

    @CanIgnoreReturnValue
    public DispatchHandlerChain addLast(DispatchChainHandler handler) {
        addLast(new Node(handler));
        return this;
    }

    @CanIgnoreReturnValue
    public DispatchHandlerChain addAll(Collection<? extends DispatchChainHandler> handlers) {
        for (DispatchChainHandler handler : handlers) {
            addLast(handler);
        }
        return this;
    }

    @CanIgnoreReturnValue
    public DispatchHandlerChain addAll(DispatchHandlerChain chain) {
        LinkedHandlerChainNode<DispatchContext> node = chain.head().next();
        while (node != null) {
            if (node instanceof Node dispatchNode) {
                addLast(dispatchNode.handler());
            }
            node = node.next();
        }
        return this;
    }

    @Override
    public CompletableFuture<Void> sparkChainHandler(DispatchContext context) {
        return CompletableFuture
                .supplyAsync(() -> super.sparkChainHandler(context), executor)
                .thenCompose(Function.identity());
    }

    @Override
    public CompletableFuture<Void> next(DispatchContext context) {
        return this.sparkChainHandler(context);
    }

    static final class Node implements LinkedHandlerChainNode<DispatchContext>, AutoCloseable {

        private final DispatchChainHandler handler;
        private @Nullable LinkedHandlerChainNode<DispatchContext> next;

        Node(DispatchChainHandler handler) {
            this.handler = handler;
        }

        private DispatchChainHandler handler() {
            return handler;
        }

        @Override
        public @Nullable LinkedHandlerChainNode<DispatchContext> next() {
            return next;
        }

        @Override
        public void next(@Nullable LinkedHandlerChainNode<DispatchContext> next) {
            this.next = next;
        }

        @Override
        public CompletableFuture<Void> sparkChainHandler(DispatchContext context) {
            LinkedHandlerChainNode<DispatchContext> chain = next;
            if (chain == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (chain instanceof Node dispatchNode) {
                return dispatchNode.handler.handle(context, dispatchNode);
            }
            return chain.sparkChainHandler(context);
        }

        @Override
        public CompletableFuture<Void> next(DispatchContext context) {
            return sparkChainHandler(context);
        }

        @Override
        public void close() throws Exception {
            if (handler instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }
}
