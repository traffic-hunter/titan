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
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Owns the inbound and outbound handler chains for one channel.
 *
 * <p>The structure is split into two independent, forward-only chains. Inbound events are
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
 * <p>Handlers continue propagation by calling the matching {@code spark*} method on the supplied
 * context. If an outbound event reaches the end of the outbound chain, the fully transformed
 * buffer is written through {@link NetChannel.Internal} without entering the chain again. If an
 * inbound buffer reaches its terminal node, it is released because no application handler has
 * claimed it.</p>
 *
 * <p>A {@link ChannelDuplexHandler} is inserted into both chains in opposite structural
 * directions. This preserves one logical nesting position: an inbound event enters the duplex
 * handler before later protocol handlers, while an outbound event unwinds through it after those
 * handlers. TLS can therefore decrypt before protocol decoding and encrypt after protocol
 * encoding while sharing one handler instance.</p>
 *
 * <p>The chain is designed for event-loop confinement and does not synchronize structural
 * mutations. Configure handlers before channel traffic starts, or add and remove them only from
 * the owning event loop. Closing is idempotent and uses identity-based deduplication so a duplex
 * {@link AutoCloseable} handler is closed exactly once.</p>
 *
 * @author yun
 */
@Slf4j
public class ChannelHandlerChain {

    private final ChannelOutBoundHandlerChainImpl outboundChain;
    private final ChannelInBoundHandlerChainImpl inboundChain;
    private boolean closed;

    public ChannelHandlerChain() {
        inboundChain = new ChannelInBoundHandlerChainImpl();
        outboundChain = new ChannelOutBoundHandlerChainImpl();
    }

    /** Inserts an inbound handler so it observes inbound events before existing handlers. */
    @CanIgnoreReturnValue
    public ChannelHandlerChain addFirst(ChannelInBoundHandler handler) {
        inboundChain.addFirst(handler);
        return this;
    }

    /** Inserts an outbound handler so it observes writes before existing outbound handlers. */
    @CanIgnoreReturnValue
    public ChannelHandlerChain addFirst(ChannelOutBoundHandler handler) {
        outboundChain.addFirst(handler);
        return this;
    }

    /**
     * Wraps the existing chains with a duplex handler at their first logical position.
     *
     * <p>The handler is first for inbound events and last for outbound events.</p>
     */
    @CanIgnoreReturnValue
    public ChannelHandlerChain addFirst(ChannelDuplexHandler handler) {
        addFirst((ChannelInBoundHandler) handler);
        addLast((ChannelOutBoundHandler) handler);
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
    public ChannelHandlerChain add(ChannelDuplexHandler handler) {
        return addLast(handler);
    }

    /** Appends an inbound handler after the existing inbound handlers. */
    @CanIgnoreReturnValue
    public ChannelHandlerChain addLast(ChannelInBoundHandler handler) {
        inboundChain.addLast(handler);
        return this;
    }

    /** Appends an outbound handler immediately before the terminal raw write. */
    @CanIgnoreReturnValue
    public ChannelHandlerChain addLast(ChannelOutBoundHandler handler) {
        outboundChain.addLast(handler);
        return this;
    }

    /**
     * Nests a duplex handler at the last logical position.
     *
     * <p>The handler is last for inbound events and first for outbound events.</p>
     */
    @CanIgnoreReturnValue
    public ChannelHandlerChain addLast(ChannelDuplexHandler handler) {
        addLast((ChannelInBoundHandler) handler);
        addFirst((ChannelOutBoundHandler) handler);
        return this;
    }

    public boolean remove(ChannelInBoundHandler handler) {
        return inboundChain.remove(handler);
    }

    public boolean remove(ChannelOutBoundHandler handler) {
        return outboundChain.remove(handler);
    }

    public boolean remove(ChannelDuplexHandler handler) {
        return remove((ChannelInBoundHandler) handler) && remove((ChannelOutBoundHandler) handler);
    }

    void processChannelConnecting(NetChannel channel) {
        inboundChain.sparkChannelConnecting(channel);
    }

    void processChannelAfterConnected(NetChannel channel) {
        inboundChain.sparkChannelAfterConnected(channel);
    }

    void processChannelRead(NetChannel channel, Buffer buffer) {
        try {
            inboundChain.sparkChannelRead(channel, buffer);
        } catch (Exception e) {
            log.error("Failed to process read", e);
            channel.close();
        }
    }

    void processChannelWrite(NetChannel channel, Buffer buffer) {
        try {
            outboundChain.sparkChannelWrite(channel, buffer);
        } catch (Exception e) {
            log.error("Failed to process write", e);
            channel.close();
        }
    }

    /**
     * Closes stateful handlers once, including handlers installed in both chains.
     *
     * <p>All handlers are visited even if one close fails. Close failures are logged because channel
     * shutdown has no asynchronous result through which they can be returned.</p>
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        Set<Object> closedHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        inboundChain.forEachHandler(handler -> closeHandler(handler, closedHandlers));
        outboundChain.forEachHandler(handler -> closeHandler(handler, closedHandlers));

        inboundChain.clear();
        outboundChain.clear();
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

}
