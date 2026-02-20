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
package org.traffichunter.titan.core.transport.stomp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.codec.stomp.ServerSubscription;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.IdGenerator;

/**
 * @author yungwang-o
 */
@Slf4j
public class StompNetServerChannelImpl implements StompNetServerChannel {

    private static final int MAX_SUBSCRIBER = 100;

    private final String sessionId = IdGenerator.uuid();
    private final Map<String, ServerSubscription> subscriptions = new ConcurrentHashMap<>(MAX_SUBSCRIBER);

    private final NetServerChannel serverChannel;

    private long pingTimer = -1;
    private long pongTimer = -1;

    StompNetServerChannelImpl(ChannelHandShakeEventListener channelHandShakeEventListener) {
        try {
            this.serverChannel = NetServerChannel.open(channelHandShakeEventListener);
        } catch (IOException e) {
            throw new StompServerException("Not open channel", e);
        }
    }

    @Override
    public ChannelHandlerChain chain() {
        return serverChannel.chain();
    }

    @Override
    public ChannelPromise register(IOEventLoop eventLoop, ChannelPromise promise) {
        return serverChannel.register(eventLoop, promise);
    }

    @Override
    public @Nullable IOEventLoop eventLoop() {
        return serverChannel.eventLoop();
    }

    @Override
    public String id() {
        return serverChannel.id();
    }

    @Override
    public String session() {
        return sessionId;
    }

    @Override
    public void subscribe(final String id, final ServerSubscription subscription) {
        subscriptions.put(id, subscription);
    }

    @Override
    public void unsubscribe(final String id) {
        subscriptions.remove(id);
    }

    @Override
    public List<ServerSubscription> subscriptions() {
        return subscriptions.values().stream().toList();
    }

    @Override
    public void bind(InetSocketAddress address) throws IOException {
        serverChannel.bind(address);
    }

    @Override
    public @Nullable StompNetChannel accept() {
        NetChannel channel = serverChannel.accept();
        if(channel == null) {
            return null;
        }

        return new StompNetChannelImpl(channel);
    }

    @Override
    public synchronized void setHeartbeat(final long ping, final long pong, final Runnable handler) {
        if (ping > 0) {
//            pingTimer = server.setInterval(ping, handler);
        }
        if (pong > 0) {
//            pongTimer = server.setInterval(pong, () -> {
//                long d = Duration.between(lastActivatedTime, Instant.now()).toMillis();
//                if(d > pong * 2) {
//                    log.warn("Connection closed due to heartbeat timeout. = {} ms", d);
//                    close();
//                }
//            });
        }
    }

    private void cancelHeartbeat() {
        if (pingTimer >= 0) {
            //server.cancelInterval(pingTimer);
            pingTimer = -1;
        }
        if (pongTimer >= 0) {
            //server.cancelInterval(pongTimer);
            pongTimer = -1;
        }
    }

    @Override
    public <T> NetServerChannel setOption(SocketOption<T> option, T value) {
        return serverChannel.setOption(option, value);
    }

    @Override
    public @Nullable <T> T getOption(SocketOption<T> option) {
        return serverChannel.getOption(option);
    }

    @Override
    public Instant lastActivatedAt() {
        return serverChannel.lastActivatedAt();
    }

    @Override
    public Instant setLastActivatedAt() {
        return serverChannel.lastActivatedAt();
    }

    @Override
    public @Nullable SocketAddress localAddress() {
        return serverChannel.localAddress();
    }

    @Override
    public @Nullable SocketAddress remoteAddress() {
        return serverChannel.remoteAddress();
    }

    @Override
    public boolean isOpen() {
        return serverChannel.isOpen();
    }

    @Override
    public boolean isRegistered() {
        return serverChannel.isRegistered();
    }

    @Override
    public boolean isActive() {
        return serverChannel.isActive();
    }

    @Override
    public boolean isClosed() {
        return serverChannel.isClosed();
    }

    @Override
    public void close() {
        serverChannel.close();
    }
}
