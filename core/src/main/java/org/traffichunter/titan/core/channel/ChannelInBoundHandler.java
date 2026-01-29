package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

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
