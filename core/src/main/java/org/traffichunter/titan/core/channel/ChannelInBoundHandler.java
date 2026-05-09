package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Handler for channel events flowing from transport I/O toward protocol/application code.
 *
 * <p>Implementations receive a chain context and may call the matching {@code spark*} method
 * to pass the event to the next inbound handler. These callbacks may run on the channel's
 * event-loop thread, so do not run blocking code here.</p>
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
