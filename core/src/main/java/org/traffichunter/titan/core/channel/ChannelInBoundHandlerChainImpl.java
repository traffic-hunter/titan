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
package org.traffichunter.titan.core.channel;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Noop;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.function.Consumer;

/**
 * Linked inbound chain owned by a {@link ChannelHandlerChain}.
 *
 * <p>A no-op sentinel head removes special cases when inserting or removing the first handler.
 * Each node holds a {@link ChannelInBoundHandler} and provides the continuation passed to it.
 * Calling a {@code spark*} method advances to the next handler without restarting the pipeline.</p>
 *
 * <p>Connection, read, and exception events preserve handler order. When a read reaches the
 * terminal node without being consumed, the chain releases the buffer. A handler that stops
 * propagation earlier is responsible for any buffer it retains
 * or consumes.</p>
 *
 * <p>This implementation is not synchronized. Registration and removal must happen before
 * concurrent use or on the channel's event-loop thread.</p>
 *
 * @author yun, gkdbssla97
 */
public final class ChannelInBoundHandlerChainImpl
        extends AbstractLinkedHandlerChain<ChannelInBoundHandlerChainImpl.Node>
        implements ChannelInBoundHandlerChain {

    public ChannelInBoundHandlerChainImpl() {
        super(new Node(new HeadHandler()));
    }

    public ChannelInBoundHandlerChainImpl(ChannelInBoundHandler handler) {
        this();
        addLast(handler);
    }

    /** Adds a handler at the transport-facing start of inbound propagation. */
    @CanIgnoreReturnValue
    public ChannelInBoundHandlerChainImpl addFirst(ChannelInBoundHandler handler) {
        addFirst(new Node(handler));
        return this;
    }

    /** Adds a handler at the application-facing end of inbound propagation. */
    @CanIgnoreReturnValue
    public ChannelInBoundHandlerChainImpl addLast(ChannelInBoundHandler handler) {
        addLast(new Node(handler));
        return this;
    }

    /**
     * Removes the first node containing the exact handler instance.
     *
     * @return {@code true} when the handler was present
     */
    public boolean remove(ChannelInBoundHandler handler) {
        return removeFirst(node -> node.handler == handler);
    }

    void forEachHandler(Consumer<? super ChannelInBoundHandler> consumer) {
        forEach(node -> consumer.accept(node.handler));
    }

    /** Starts propagation from the sentinel head. */
    @Override
    public void sparkChannelConnecting(NetChannel channel) {
        head().sparkChannelConnecting(channel);
    }

    @Override
    public void sparkChannelAfterConnected(NetChannel channel) {
        head().sparkChannelAfterConnected(channel);
    }

    @Override
    public void sparkChannelRead(NetChannel channel, Buffer buffer) {
        head().sparkChannelRead(channel, buffer);
    }

    @Override
    public void sparkExceptionCaught(Throwable error) {
        head().sparkExceptionCaught(error);
    }

    static final class Node implements LinkedNode<Node>, ChannelInBoundHandlerChain {

        private final ChannelInBoundHandler handler;
        private @Nullable Node next;

        private Node(ChannelInBoundHandler handler) {
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
        public void sparkChannelConnecting(NetChannel channel) {
            Node chain = next;
            if (chain != null) {
                chain.handler.sparkChannelConnecting(channel, chain);
            }
        }

        @Override
        public void sparkChannelAfterConnected(NetChannel channel) {
            Node chain = next;
            if (chain != null) {
                chain.handler.sparkChannelAfterConnected(channel, chain);
            }
        }

        @Override
        public void sparkChannelRead(NetChannel channel, Buffer buffer) {
            Node chain = next;
            if (chain == null) {
                buffer.release();
                return;
            }
            chain.handler.sparkChannelRead(channel, buffer, chain);
        }

        @Override
        public void sparkExceptionCaught(Throwable error) {
            Node chain = next;
            if (chain != null) {
                chain.handler.sparkExceptionCaught(error, chain);
            }
        }
    }

    @Noop
    private static final class HeadHandler implements ChannelInBoundHandler {
    }
}
