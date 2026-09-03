package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Handles channel events from transport I/O before they reach protocol or application code.
 *
 * <p>Implementations receive a chain context and may call the matching {@code spark*} method
 * to pass the event to the next inbound handler. A handler may stop forwarding an event,
 * but one that keeps a read buffer must retain and eventually
 * release it according to the buffer ownership policy.</p>
 *
 * <p>Callbacks run on the channel event loop and must not block. Asynchronous work should
 * preserve event order.</p>
 *
 * @author yun
 */
public interface ChannelInBoundHandler {

    default void sparkChannelConnecting(NetChannel channel, ChannelInBoundHandlerChain chain) {
    }

    default void sparkChannelAfterConnected(NetChannel channel, ChannelInBoundHandlerChain chain) {
    }

    default void sparkChannelRead(NetChannel channel, Buffer buffer, ChannelInBoundHandlerChain chain) {
    }

    default void sparkExceptionCaught(Throwable error, ChannelInBoundHandlerChain chain) {
    }
}
