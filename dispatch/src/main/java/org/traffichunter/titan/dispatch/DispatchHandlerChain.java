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
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.AbstractLinkedHandlerChain;
import org.traffichunter.titan.core.channel.LinkedNode;

/**
 * Asynchronous linked handler chain for message dispatch work.
 *
 * <p>The gateway normally places routing first, custom handlers in the middle, and fanout
 * activation last. Custom handlers can validate, persist, or measure messages without changes
 * to the gateway. Each handler sees the context changes made by earlier handlers.</p>
 *
 * <p>Starting the chain schedules the complete traversal on the configured {@link Executor}.
 * Each handler decides whether to continue by invoking its supplied {@link DispatchChain}. The
 * future returned to the caller completes when traversal finishes or a handler throws.</p>
 *
 * <p>Iteration skips the no-op sentinel head. The chain manages links and calls between handlers;
 * the component that creates a handler manages its lifecycle. Changes to the links are
 * unsynchronized and should finish before dispatch begins.</p>
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
     * Sparks dispatch propagation on the configured executor.
     */
    public CompletableFuture<Void> sparkDispatch(DispatchContext context) {
        return CompletableFuture.runAsync(() -> head().next(context), executor);
    }

    static final class Node implements LinkedNode<Node>, DispatchChain {

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

        @Override
        public DispatchChain next(DispatchContext context) {
            Node chain = next;
            if (chain == null) {
                return this;
            }

            return chain.handler.handle(context, chain);
        }
    }
}
