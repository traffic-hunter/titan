/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.channel.stomp;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.codec.stomp.*;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.util.List;

/**
 * @author yungwang-o
 */
public interface StompClientChannel extends StompChannel {

    static StompClientChannel open(
            ChannelHandShakeEventListener handShakeEventListener,
            StompSessionOption option
    ) throws IOException {
        return open(handShakeEventListener, option, handler -> {});
    }

    static StompClientChannel open(
            ChannelHandShakeEventListener handShakeEventListener,
            StompSessionOption option,
            Handler<StompClientHandler> clientHandlerConfigurer
    ) throws IOException {
        return new StompClientTcpChannel(handShakeEventListener, option, clientHandlerConfigurer);
    }

    static StompClientChannel wrap(
            NetChannel netChannel,
            StompSessionOption option
    ) {
        if (netChannel instanceof WebSocketChannel webSocketChannel) {
            return wrap(webSocketChannel, option, handler -> {});
        }

        return wrap(netChannel, option, handler -> {});
    }

    static StompClientChannel wrap(
            NetChannel netChannel,
            StompSessionOption option,
            Handler<StompClientHandler> clientHandlerConfigurer
    ) {
        if (netChannel instanceof WebSocketChannel webSocketChannel) {
            return new StompClientWebSocketChannel(webSocketChannel, option, clientHandlerConfigurer);
        }
        return new StompClientTcpChannel(netChannel, option, clientHandlerConfigurer);
    }

    @Override
    NetChannel channel();

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

    StompClientHandler handler();

    List<StompClientSubscription> subscriptions();

    void setHeartbeat(long ping, long pong, Runnable handler);

    void receipt(String receiptId);

    void connected();

    void failConnect(Throwable error);

    @CanIgnoreReturnValue
    StompClientChannel closeHandler(Handler<StompClientChannel> handler);

    @CanIgnoreReturnValue
    StompClientChannel connectionDroppedHandler(Handler<StompClientChannel> handler);

    @CanIgnoreReturnValue
    StompClientChannel exceptionHandler(Handler<Throwable> handler);

    Promise<Void> connectedPromise();

    boolean isConnected();
}
