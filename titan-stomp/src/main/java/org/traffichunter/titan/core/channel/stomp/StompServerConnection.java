/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.channel.stomp;

import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.NetServerChannel;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * @author yungwang-o
 */
public interface StompServerConnection extends StompConnection {

    static StompServerConnection open(
            ChannelHandShakeEventListener channelHandShakeEventListener,
            StompServerOption option,
            StompServerHandler handler
    ) {
        return new StompServerConnectionImpl(channelHandShakeEventListener, option, handler);
    }

    static StompServerConnection wrap(
            NetServerChannel serverChannel,
            StompServerOption option,
            StompServerHandler handler
    ) {
        return new StompServerConnectionImpl(serverChannel, option, handler);
    }

    static StompServerConnection create(
            StompServerOption option,
            StompServerHandler handler
    ) {
        return new StompServerConnectionImpl(option, handler);
    }

    default Promise<Void> write(StompFrame frame) {
        return write(frame.toBuffer());
    }

    Promise<Void> write(Buffer buffer);

    void bind(NetServerChannel serverChannel);

    void registerConnection(StompClientConnection connection);

    void unregisterConnection(String sessionId);

    @Nullable StompClientConnection findConnection(String sessionId);

    Collection<StompClientConnection> connections();

    int connectionCount();
}
