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
package org.traffichunter.titan.dispatch;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.channel.chain.AbstractLinkedHandlerChain;
import org.traffichunter.titan.core.util.channel.chain.LinkedNode;

/**
 * Asynchronous linked handler chain for message dispatch work.
 *
 * <p>The gateway normally assembles routing first, optional user handlers in the middle, and
 * fanout activation last. This permits cross-cutting stages such as validation, persistence, or
 * metrics to participate without coupling them directly to the gateway. Handler order is
 * significant because each stage observes mutations made by all preceding stages.</p>
 *
 * <p>Starting the chain schedules its first step on the configured {@link Executor}. Each node
 * waits for its handler's {@link CompletableFuture} to complete before advancing automatically.
 * The future returned to the caller completes after every handler has completed, or completes
 * exceptionally as soon as one stage fails.</p>
 *
 * <p>A no-op sentinel head is excluded from iteration. The chain only manages structure and
 * propagation; lifecycle ownership remains with the component that creates a handler. Structural
 * mutation is unsynchronized and should finish before dispatch begins.</p>
 *
 * @author yun
 */
public class DispatchHandlerChain extends AbstractLinkedHandlerChain<DispatchHandlerChain.Node> {

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

    /** Appends a handler to the end of the dispatch lifecycle. */
    @CanIgnoreReturnValue
    public DispatchHandlerChain add(DispatchChainHandler handler) {
        return addLast(handler);
    }

    /** Inserts a handler before every existing user handler. */
    @CanIgnoreReturnValue
    public DispatchHandlerChain addFirst(DispatchChainHandler handler) {
        addFirst(new Node(handler));
        return this;
    }

    /** Appends a handler after every existing user handler. */
    @CanIgnoreReturnValue
    public DispatchHandlerChain addLast(DispatchChainHandler handler) {
        addLast(new Node(handler));
        return this;
    }

    /** Appends all handlers in iteration order. */
    @CanIgnoreReturnValue
    public DispatchHandlerChain addAll(Collection<? extends DispatchChainHandler> handlers) {
        for (DispatchChainHandler handler : handlers) {
            addLast(handler);
        }
        return this;
    }

    /**
     * Enters the chain on the configured executor.
     */
    public CompletableFuture<Void> dispatch(DispatchContext context) {
        return CompletableFuture
                .supplyAsync(() -> head().dispatch(context, executor), executor)
                .thenCompose(Function.identity());
    }

    static final class Node implements LinkedNode<Node> {

        private final DispatchChainHandler handler;
        private @Nullable Node next;

        Node(DispatchChainHandler handler) {
            this.handler = handler;
        }

        @Override
        public @Nullable Node next() {
            return next;
        }

        @Override
        public void next(@Nullable Node next) {
            this.next = next;
        }

        private CompletableFuture<Void> dispatch(DispatchContext context, Executor executor) {
            return handler.handle(context).thenComposeAsync(ignored -> {
                Node chain = next;
                if (chain == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return chain.dispatch(context, executor);
            }, executor);
        }
    }
}
