package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Handler for channel events flowing from transport I/O toward protocol/application code.
 *
 * <p>Implementations receive a chain context and may call the matching {@code spark*} method
 * to pass the event to the next inbound handler. Not forwarding an event is a valid
 * short-circuiting operation, but a handler that keeps a read buffer must retain and eventually
 * release it according to the buffer ownership policy.</p>
 *
 * <p>Callbacks run under the channel event-loop execution model. Implementations must avoid
 * blocking work and should preserve event ordering when asynchronous work is unavoidable.</p>
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
