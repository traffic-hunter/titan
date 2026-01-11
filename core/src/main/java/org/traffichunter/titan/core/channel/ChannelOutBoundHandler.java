package org.traffichunter.titan.core.channel;

import org.traffichunter.titan.core.util.buffer.Buffer;

public interface ChannelOutBoundHandler {

    void sparkChannelWrite(Channel channel, Buffer buffer);
}
