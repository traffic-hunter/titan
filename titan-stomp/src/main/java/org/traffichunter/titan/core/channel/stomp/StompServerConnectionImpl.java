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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.codec.stomp.StompSubscriptions;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.jspecify.annotations.Nullable;

/**
 * @author yungwang-o
 */
public class StompServerConnectionImpl implements StompServerConnection {

    private @Nullable NetServerChannel serverChannel;
    private final StompServerOption option;
    private final Map<String, StompClientConnection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinSequence = new AtomicInteger();
    private final StompSubscriptions<StompServerSubscription> subscriptions = new StompSubscriptions<>();

    StompServerConnectionImpl(
            ChannelHandShakeEventListener channelHandShakeEventListener,
            StompServerOption option
    ) {
        try {
            this.serverChannel = NetServerChannel.open(channelHandShakeEventListener);
            this.option = option;
        } catch (IOException e) {
            throw new StompNetServeChannelException("Not open channel", e);
        }
    }

    StompServerConnectionImpl(StompServerOption option) {
        this.option = option;
    }

    StompServerConnectionImpl(NetServerChannel serverChannel, StompServerOption option) {
        this.serverChannel = serverChannel;
        this.option = option;
    }

    @Override
    public Channel channel() {
        return requireServerChannel();
    }

    @Override
    public String session() {
        return requireServerChannel().session();
    }

    @Override
    public Promise<Void> write(Buffer buffer) {
        StompClientConnection connection = nextConnection();
        if (connection == null) {
            return Promise.failedPromise(
                    requireServerChannel().eventLoop(),
                    new StompNetServeChannelException("No active STOMP client connections")
            );
        }

        NetChannel netChannel = asNetChannel(connection);
        return netChannel.eventLoop().submit(() -> netChannel.writeAndFlush(buffer));
    }

    @Override
    public void register(StompClientConnection connection) {
        connections.put(connection.session(), connection);
    }

    @Override
    public void unregister(String sessionId) {
        connections.remove(sessionId);
    }

    @Override
    public @Nullable StompClientConnection findConnection(String sessionId) {
        return connections.get(sessionId);
    }

    @Override
    public List<StompClientConnection> connections() {
        return List.copyOf(connections.values());
    }

    @Override
    public StompClientConnection connection() {
        StompClientConnection stompClientConnection = nextConnection();
        if (stompClientConnection == null) {
            throw new StompNetServeChannelException("No active STOMP client connection");
        }

        return stompClientConnection;
    }

    @Override
    public StompSubscriptions<StompServerSubscription> subscriptions() {
        return subscriptions;
    }

    @Override
    public Instant setLastActivatedAt() {
        return requireServerChannel().setLastActivatedAt();
    }

    @Override
    public Instant lastActivatedAt() {
        return requireServerChannel().lastActivatedAt();
    }

    @Override
    public String version() {
        return option.stompVersion().getVersion();
    }

    @Override
    public void close() {
        connections.values().forEach(StompClientConnection::close);
        connections.clear();
        requireServerChannel().close();
    }

    @Override
    public void bind(NetServerChannel serverChannel) {
        this.serverChannel = serverChannel;
    }

    @Override
    public StompServerOption option() {
        return option;
    }

    private @Nullable StompClientConnection nextConnection() {
        List<StompClientConnection> activeConnections = activeConnections();
        if (activeConnections.isEmpty()) {
            return null;
        }

        int index = Math.floorMod(roundRobinSequence.getAndIncrement(), activeConnections.size());
        return activeConnections.get(index);
    }

    private List<StompClientConnection> activeConnections() {
        return connections.values().stream()
                .filter(this::isActive)
                .toList();
    }

    private boolean isActive(StompClientConnection connection) {
        Channel channel = connection.channel();
        if (channel.isClosed() || !channel.isActive()) {
            unregister(connection.session());
            return false;
        }

        return true;
    }

    private NetChannel asNetChannel(StompClientConnection connection) {
        return connection.channel();
    }

    private NetServerChannel requireServerChannel() {
        NetServerChannel channel = serverChannel;
        if (channel == null) {
            throw new StompNetServeChannelException("Server channel is not bound");
        }
        return channel;
    }
}
