package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

public interface ChannelOutBoundHandler {

    default void sparkChannelWrite(NetChannel channel, Buffer buffer, ChannelOutBoundHandlerChainImpl chain) {}

    default void sparkExceptionCaught(Throwable error, ChannelOutBoundHandlerChainImpl chain) {}
}
