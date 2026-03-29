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
import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.stomp.*;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.util.List;

/**
 * @author yungwang-o
 */
public interface StompClientConnection extends StompConnection {

    static StompClientConnection open(
            ChannelHandShakeEventListener handShakeEventListener,
            StompClientOption option
    ) throws IOException {
        return new StompClientConnectionImpl(handShakeEventListener, option);
    }

    static StompClientConnection wrap(
            NetChannel netChannel,
            StompClientOption option
    ) {
        return new StompClientConnectionImpl(netChannel, option);
    }

    @CanIgnoreReturnValue
    Promise<StompFrame> disconnect();

    @CanIgnoreReturnValue
    Promise<StompFrame> disconnect(StompFrame frame);

    @CanIgnoreReturnValue
    Promise<StompFrame> begin(String id);

    @CanIgnoreReturnValue
    Promise<StompFrame> begin(String id, StompHeaders headers);

    @CanIgnoreReturnValue
    Promise<StompFrame> commit(String id);

    @CanIgnoreReturnValue
    Promise<StompFrame> commit(String id, StompHeaders headers);

    @CanIgnoreReturnValue
    Promise<StompFrame> abort(String id);

    @CanIgnoreReturnValue
    Promise<StompFrame> abort(String id, StompHeaders headers);

    @CanIgnoreReturnValue
    Promise<StompFrame> ack(String id);

    @CanIgnoreReturnValue
    Promise<StompFrame> ack(String id, String txId);

    @CanIgnoreReturnValue
    Promise<StompFrame> nack(String id);

    @CanIgnoreReturnValue
    Promise<StompFrame> nack(String id, String txId);

    @CanIgnoreReturnValue
    Promise<StompFrame> send(String destination, Buffer body);

    @CanIgnoreReturnValue
    Promise<StompFrame> send(String destination, Buffer body, StompHeaders headers);

    @CanIgnoreReturnValue
    Promise<StompFrame> send(StompFrame frame);

    @CanIgnoreReturnValue
    Promise<StompFrame> send(String destination, StompFrame body);

    @CanIgnoreReturnValue
    Promise<StompFrame> subscribe(String destination);

    @CanIgnoreReturnValue
    Promise<StompFrame> subscribe(String destination, StompHeaders headers);

    @CanIgnoreReturnValue
    Promise<StompFrame> subscribe(String destination, Handler<StompFrame> handler);

    @CanIgnoreReturnValue
    Promise<StompFrame> subscribe(String destination, StompHeaders headers, Handler<StompFrame> handler);

    @CanIgnoreReturnValue
    Promise<StompFrame> unsubscribe(String destination);

    @CanIgnoreReturnValue
    Promise<StompFrame> unsubscribe(String destination, StompHeaders headers);

    @CanIgnoreReturnValue
    Promise<StompFrame> error(StompFrame frame);

    StompHandler handler();

    /**
     * @return read-only list
     */
    List<Subscription> subscriptions();

    void setHeartbeat(long ping, long pong, Runnable handler);

    void registerInboundSubscription(String id, String destination, String ackMode);

    void removeInboundSubscription(String id);

    void receipt(String receiptId);

    void connected();

    void failConnect(Throwable error);

    boolean isConnected();
}
