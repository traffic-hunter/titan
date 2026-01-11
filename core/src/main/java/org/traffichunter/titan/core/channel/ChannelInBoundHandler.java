package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

public interface ChannelInBoundHandler {

    void sparkChannelConnecting(NetChannel channel);

    void sparkChannelAfterConnected(NetChannel channel);

    void sparkChannelRead(NetChannel channel, Buffer buffer);

    void sparkExceptionCaught(Throwable error);
}
