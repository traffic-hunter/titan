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
 * Linked outbound chain owned by a {@link ChannelHandlerChain}.
 *
 * <p>Writes enter through a no-op sentinel head and visit handlers in chain order. Each node is the
 * continuation for the handler stored immediately after it. Forwarding resumes from the current
 * position without running earlier encoders again.</p>
 *
 * <p>At the terminal node, the resulting buffer is written through {@link NetChannel.Internal}.
 * Calling the public channel write API here would run the same outbound handlers again.
 * If the raw write fails synchronously before ownership is transferred,
 * the terminal node releases the buffer and rethrows the failure.</p>
 *
 * <p>This implementation is not synchronized. Registration and removal must happen before
 * concurrent use or on the channel's event-loop thread.</p>
 *
 * @author yun
 */
public final class ChannelOutBoundHandlerChainImpl
        extends AbstractLinkedHandlerChain<ChannelOutBoundHandlerChainImpl.Node>
        implements ChannelOutBoundHandlerChain {

    public ChannelOutBoundHandlerChainImpl() {
        super(new Node(new HeadHandler()));
    }

    /** Adds a handler at the application-facing start of outbound propagation. */
    @CanIgnoreReturnValue
    public ChannelOutBoundHandlerChainImpl addFirst(ChannelOutBoundHandler handler) {
        addFirst(new Node(handler));
        return this;
    }

    /** Adds a handler immediately before the terminal raw transport write. */
    @CanIgnoreReturnValue
    public ChannelOutBoundHandlerChainImpl addLast(ChannelOutBoundHandler handler) {
        addLast(new Node(handler));
        return this;
    }

    /**
     * Removes the first node containing the exact handler instance.
     *
     * @return {@code true} when the handler was present
     */
    public boolean remove(ChannelOutBoundHandler handler) {
        return removeFirst(node -> node.handler == handler);
    }

    void forEachHandler(Consumer<? super ChannelOutBoundHandler> consumer) {
        forEach(node -> consumer.accept(node.handler));
    }

    /** Starts write propagation from the sentinel head. */
    @Override
    public void sparkChannelWrite(NetChannel channel, Buffer buffer) {
        head().sparkChannelWrite(channel, buffer);
    }

    @Override
    public void sparkExceptionCaught(Throwable error) {
        head().sparkExceptionCaught(error);
    }

    static final class Node implements LinkedNode<Node>, ChannelOutBoundHandlerChain {

        private final ChannelOutBoundHandler handler;
        private @Nullable Node next;

        private Node(ChannelOutBoundHandler handler) {
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
        public void sparkChannelWrite(NetChannel channel, Buffer buffer) {
            Node chain = next;
            if (chain == null) {
                try {
                    channel.internal().write(buffer);
                } catch (RuntimeException e) {
                    buffer.release();
                    throw e;
                }
                return;
            }
            chain.handler.sparkChannelWrite(channel, buffer, chain);
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
    private static final class HeadHandler implements ChannelOutBoundHandler {
    }
}
