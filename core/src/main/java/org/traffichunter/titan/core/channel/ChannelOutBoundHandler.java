package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

import java.util.List;

/**
 * Handler for write events flowing toward the underlying network channel.
 *
 * <p>Implementations may transform, encode, or observe outbound buffers before forwarding the
 * event to the next outbound handler. A transformed buffer continues from the current chain
 * position, preventing earlier encoders from running again. A handler that intentionally stops a
 * write or replaces its input must follow the channel buffer ownership policy and settle the
 * promise itself, since nothing downstream will see it.</p>
 *
 * <p>Callbacks run on the channel event loop and must not block.</p>
 *
 * @author yun
 */
public interface ChannelOutBoundHandler {

    /** Passes the write onward unchanged, so a handler that ignores writes never stalls a promise. */
    default void sparkChannelWrite(
            NetChannel channel,
            Buffer buffer,
            ChannelPromise promise,
            ChannelOutBoundHandlerChain chain
    ) {
        chain.sparkChannelWrite(channel, buffer, promise);
    }

    /**
     * Passes a multi-buffer request onward unchanged. Such requests are already transport-ready,
     * so codecs leave them alone.
     */
    default void sparkChannelWrite(
            NetChannel channel,
            List<Buffer> buffers,
            ChannelPromise promise,
            ChannelOutBoundHandlerChain chain
    ) {
        chain.sparkChannelWrite(channel, buffers, promise);
    }

    default void sparkExceptionCaught(Throwable error, ChannelOutBoundHandlerChain chain) {}
}
