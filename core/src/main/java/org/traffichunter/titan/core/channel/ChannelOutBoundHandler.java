package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Handler for write events flowing toward the underlying network channel.
 *
 * <p>Implementations may transform, encode, or observe outbound buffers before forwarding the
 * event to the next outbound handler. These callbacks may run on the channel's event-loop
 * thread, so do not run blocking code here.</p>
 */
public interface ChannelOutBoundHandler {

    default void sparkChannelWrite(NetChannel channel, Buffer buffer, ChannelOutBoundHandlerChainImpl chain) {}

    default void sparkExceptionCaught(Throwable error, ChannelOutBoundHandlerChainImpl chain) {}
}
