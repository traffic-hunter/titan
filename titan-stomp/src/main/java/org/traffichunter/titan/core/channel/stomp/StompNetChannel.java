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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.stomp.*;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.util.List;

/**
 * @author yungwang-o
 */
public interface StompNetChannel extends Channel{

    static StompNetChannel open(ChannelHandShakeEventListener handShakeEventListener, StompVersion stompVersion) throws IOException {
        return new StompNetChannelImpl(handShakeEventListener, stompVersion);
    }

    static StompNetChannel open(NetChannel netChannel, StompVersion stompVersion) {
        return new StompNetChannelImpl(netChannel, stompVersion);
    }

    @CanIgnoreReturnValue
    ChannelPromise disconnect();

    @CanIgnoreReturnValue
    ChannelPromise disconnect(StompFrame frame);

    @CanIgnoreReturnValue
    ChannelPromise begin(String id);

    @CanIgnoreReturnValue
    ChannelPromise begin(String id, StompHeaders headers);

    @CanIgnoreReturnValue
    ChannelPromise commit(String id);

    @CanIgnoreReturnValue
    ChannelPromise commit(String id, StompHeaders headers);

    @CanIgnoreReturnValue
    ChannelPromise abort(String id);

    @CanIgnoreReturnValue
    ChannelPromise abort(String id, StompHeaders headers);

    @CanIgnoreReturnValue
    ChannelPromise ack(String id);

    @CanIgnoreReturnValue
    ChannelPromise ack(String id, String txId);

    @CanIgnoreReturnValue
    ChannelPromise nack(String id);

    @CanIgnoreReturnValue
    ChannelPromise nack(String id, String txId);

    @CanIgnoreReturnValue
    ChannelPromise send(String destination, Buffer body);

    @CanIgnoreReturnValue
    ChannelPromise send(String destination, Buffer body, StompHeaders headers);

    @CanIgnoreReturnValue
    ChannelPromise send(StompFrame frame);

    @CanIgnoreReturnValue
    ChannelPromise send(String destination, StompFrame body);

    @CanIgnoreReturnValue
    ChannelPromise subscribe(String destination);

    @CanIgnoreReturnValue
    ChannelPromise subscribe(String destination, StompHeaders headers);

    @CanIgnoreReturnValue
    ChannelPromise subscribe(String destination, Handler<StompFrame> handler);

    @CanIgnoreReturnValue
    ChannelPromise subscribe(String destination, StompHeaders headers, Handler<StompFrame> handler);

    @CanIgnoreReturnValue
    ChannelPromise unsubscribe(String destination);

    @CanIgnoreReturnValue
    ChannelPromise unsubscribe(String destination, StompHeaders headers);

    @CanIgnoreReturnValue
    ChannelPromise error(StompFrame frame);

    /**
     * @return read-only list
     */
    List<Subscription> subscriptions();

    StompHandler handler();

    void setHeartbeat(long ping, long pong, Runnable handler);

    void registerInboundSubscription(String id, String destination, String ackMode);

    void removeInboundSubscription(String id);

    void receipt(String receiptId);

    String version();
}
