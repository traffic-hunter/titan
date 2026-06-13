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
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author yungwang-o
 */
public interface StompServerChannel extends StompChannel {

    static StompServerChannel open(
            ChannelHandShakeEventListener channelHandShakeEventListener,
            StompServerOption option
    ) {
        return new StompServerChannelImpl(channelHandShakeEventListener, option);
    }

    static StompServerChannel wrap(NetServerChannel serverChannel, StompServerOption option) {
        return new StompServerChannelImpl(serverChannel, option);
    }

    default Promise<Void> write(StompFrame frame) {
        return write(frame.toBuffer());
    }

    Promise<Void> write(Buffer buffer);

    void register(StompClientChannel connection);

    void unregister(String sessionId);

    void cleanUp(StompClientChannel connection);

    void cleanupInactiveConnections();

    @Nullable StompClientChannel findConnection(String sessionId);

    List<StompClientChannel> connections();

    StompClientChannel connection();

    StompServerSubscriptions subscriptions();

    StompServerOption option();
}
