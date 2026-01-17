package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

public interface ChannelOutBoundHandler {

    void sparkChannelWrite(NetChannel channel, Buffer buffer);
}
