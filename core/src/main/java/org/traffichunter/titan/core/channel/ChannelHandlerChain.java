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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Noop;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Owns the inbound and outbound handler pipelines for a channel.
 *
 * <p>The chain is split into two independent, forward-only pipelines. Inbound events are
 * produced by the transport when a channel connects or reads bytes. Outbound events are
 * produced by user code or codecs before bytes are finally written to the channel.</p>
 *
 * <pre>{@code
 *                         ChannelHandlerChain
 *
 *   inbound events
 *   (connect/read)
 *        |
 *        v
 *   +----------+     +-----------------+     +-----------------+
 *   | inHead   | --> | inbound handler | --> | inbound handler | --> ...
 *   +----------+     +-----------------+     +-----------------+
 *        ^
 *        |
 *   processChannelConnecting(...)
 *   processChannelAfterConnected(...)
 *   processChannelRead(...)
 *
 *
 *   outbound events
 *   (write)
 *        |
 *        v
 *   +----------+     +------------------+     +------------------+     +---------------+
 *   | outHead  | --> | outbound handler | --> | outbound handler | --> | channel.write |
 *   +----------+     +------------------+     +------------------+     +---------------+
 *        ^
 *        |
 *   processChannelWrite(...)
 * }</pre>
 *
 * <p>Handlers continue propagation by calling the {@code spark*} method on the supplied chain
 * context. If an outbound event reaches the end of the outbound chain, the fully transformed
 * buffer is written through {@link NetChannel.Internal} without entering the pipeline again.</p>
 *
 * @author yun
 */
@Slf4j
public class ChannelHandlerChain implements AutoCloseable {

    private final ChannelOutBoundHandlerChainImpl outHead;
    private ChannelOutBoundHandlerChainImpl outTail;

    private final ChannelInBoundHandlerChainImpl inHead;
    private ChannelInBoundHandlerChainImpl inTail;
    private boolean closed;

    public ChannelHandlerChain() {
        inHead = inTail = new ChannelInBoundHandlerChainImpl(new ChannelInBoundHandlerHead());
        outHead = outTail = new ChannelOutBoundHandlerChainImpl(new ChannelOutBoundHandlerHead());
    }

    @CanIgnoreReturnValue
    public ChannelHandlerChain addFirst(ChannelInBoundHandler handler) {
        ChannelInBoundHandlerChainImpl context = new ChannelInBoundHandlerChainImpl(handler);
        context.next = inHead.next;
        inHead.next = context;
        if (inTail == inHead) {
            inTail = context;
        }

        return this;
    }

    @CanIgnoreReturnValue
    public ChannelHandlerChain addFirst(ChannelOutBoundHandler handler) {
        ChannelOutBoundHandlerChainImpl context = new ChannelOutBoundHandlerChainImpl(handler);
        context.next = outHead.next;
        outHead.next = context;
        if (outTail == outHead) {
            outTail = context;
        }

        return this;
    }

    @CanIgnoreReturnValue
    public ChannelHandlerChain add(ChannelInBoundHandler handler) {
        return addLast(handler);
    }

    @CanIgnoreReturnValue
    public ChannelHandlerChain add(ChannelOutBoundHandler handler) {
        return addLast(handler);
    }

    @CanIgnoreReturnValue
    public ChannelHandlerChain addLast(ChannelInBoundHandler handler) {
        ChannelInBoundHandlerChainImpl context = new ChannelInBoundHandlerChainImpl(handler);
        inTail.next = context;
        inTail = context;

        return this;
    }

    @CanIgnoreReturnValue
    public ChannelHandlerChain addLast(ChannelOutBoundHandler handler) {
        ChannelOutBoundHandlerChainImpl context = new ChannelOutBoundHandlerChainImpl(handler);
        outTail.next = context;
        outTail = context;

        return this;
    }

    public boolean remove(ChannelInBoundHandler handler) {
        ChannelInBoundHandlerChainImpl previous = inHead;
        ChannelInBoundHandlerChainImpl current = inHead.next;

        while (current != null) {
            if (current.handler == handler) {
                previous.next = current.next;
                if (inTail == current) {
                    inTail = previous;
                }
                current.next = null;
                return true;
            }

            previous = current;
            current = current.next;
        }

        return false;
    }

    public boolean remove(ChannelOutBoundHandler handler) {
        ChannelOutBoundHandlerChainImpl previous = outHead;
        ChannelOutBoundHandlerChainImpl current = outHead.next;

        while (current != null) {
            if (current.handler == handler) {
                previous.next = current.next;
                if (outTail == current) {
                    outTail = previous;
                }
                current.next = null;
                return true;
            }

            previous = current;
            current = current.next;
        }

        return false;
    }

    void processChannelConnecting(NetChannel channel) {
        inHead.sparkChannelConnecting(channel);
    }

    void processChannelAfterConnected(NetChannel channel) {
        inHead.sparkChannelAfterConnected(channel);
    }

    void processChannelRead(NetChannel channel, Buffer buffer) {
        try {
            inHead.sparkChannelRead(channel, buffer);
        } catch (Exception e) {
            log.error("Failed to process read", e);
            channel.close();
        }
    }

    void processChannelWrite(NetChannel channel, Buffer buffer) {
        try {
            outHead.sparkChannelWrite(channel, buffer);
        } catch (Exception e) {
            log.error("Failed to process write", e);
            channel.close();
        }
    }

    /**
     * Closes stateful handlers once, including handlers installed in both pipelines.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        Set<Object> closedHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        ChannelInBoundHandlerChainImpl inbound = inHead.next;
        while (inbound != null) {
            closeHandler(inbound.handler, closedHandlers);
            inbound = inbound.next;
        }

        ChannelOutBoundHandlerChainImpl outbound = outHead.next;
        while (outbound != null) {
            closeHandler(outbound.handler, closedHandlers);
            outbound = outbound.next;
        }
    }

    private static void closeHandler(Object handler, Set<Object> closedHandlers) {
        if (!(handler instanceof AutoCloseable closeable) || !closedHandlers.add(handler)) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception e) {
            log.warn("Failed to close channel handler {}", handler.getClass().getName(), e);
        }
    }

    @Noop
    private static class ChannelInBoundHandlerHead implements ChannelInBoundHandler { }

    @Noop
    private static class ChannelOutBoundHandlerHead implements ChannelOutBoundHandler { }
}
