package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Handler for write events flowing toward the underlying network channel.
 *
 * <p>Implementations may transform, encode, or observe outbound buffers before forwarding the
 * event to the next outbound handler. A transformed buffer continues from the current chain
 * position, preventing earlier encoders from running again. A handler that intentionally stops a
 * write or replaces its input must follow the channel buffer ownership policy.</p>
 *
 * <p>Callbacks run on the channel event loop and must not block.</p>
 *
 * @author yun
 */
public interface ChannelOutBoundHandler {

    default void sparkChannelWrite(NetChannel channel, Buffer buffer, ChannelOutBoundHandlerChain chain) {}

    default void sparkExceptionCaught(Throwable error, ChannelOutBoundHandlerChain chain) {}
}
