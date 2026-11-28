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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.AbstractLinkedHandlerChain;
import org.traffichunter.titan.core.channel.LinkedNode;

/**
 * Linked chain of dispatch handlers.
 *
 * <p>The gateway puts routing first and custom handlers after it. The dispatcher runs its own
 * chain for each message before export. Custom handlers can validate, persist or measure
 * messages without changes to the gateway. Each handler sees the changes earlier handlers made
 * to the context.</p>
 *
 * <p>{@link #sparkDispatch(DispatchContext)} runs the whole chain on the chain's
 * {@link Executor}, which by default is the calling thread. Each handler decides whether to go on
 * by calling its {@link DispatchChain}. The returned future completes when the chain finishes or
 * a handler throws.</p>
 *
 * <p>Iteration skips the no-op head node. The chain only links and calls handlers; whoever
 * creates a handler owns its lifecycle. The links are not synchronized, so add handlers before
 * dispatch starts.</p>
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

    /** Appends a handler to the end of the chain. */
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
     * Runs the chain for the context on the chain's executor.
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
                context.pass();
                return this;
            }

            return chain.handler.handle(context, chain);
        }
    }
}
