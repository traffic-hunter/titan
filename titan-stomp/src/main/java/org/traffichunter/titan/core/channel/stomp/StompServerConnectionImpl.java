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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.codec.stomp.Transactions;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.jspecify.annotations.Nullable;

/**
 * @author yungwang-o
 */
public class StompServerConnectionImpl implements StompServerConnection {

    private final NetServerChannel serverChannel;
    private final StompServerOption option;
    private final Map<String, StompClientConnection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinSequence = new AtomicInteger();
    private final StompServerSubscriptions subscriptions = new StompServerSubscriptions();

    private final AtomicReference<State> state = new AtomicReference<>(State.INIT);

    enum State {
        INIT,
        RUNNING,
        CLOSING,
        CLOSED
    }

    StompServerConnectionImpl(
            ChannelHandShakeEventListener channelHandShakeEventListener,
            StompServerOption option
    ) {
        try {
            this.serverChannel = NetServerChannel.open(channelHandShakeEventListener);
            this.option = option;
            state.set(State.RUNNING);
        } catch (IOException e) {
            throw new StompNetServeChannelException("Not open channel", e);
        }
    }

    StompServerConnectionImpl(NetServerChannel serverChannel, StompServerOption option) {
        this.serverChannel = serverChannel;
        this.option = option;
        state.set(State.RUNNING);
    }

    @Override
    public Channel channel() {
        return serverChannel;
    }

    @Override
    public String session() {
        return serverChannel.session();
    }

    @Override
    public Promise<Void> write(Buffer buffer) {
        StompClientConnection connection = nextConnection();
        if (connection == null) {
            return Promise.failedPromise(
                    serverChannel.eventLoop(),
                    new StompNetServeChannelException("No active STOMP client connections")
            );
        }

        NetChannel netChannel = asNetChannel(connection);
        return netChannel.eventLoop().submit(() -> netChannel.writeAndFlush(buffer));
    }

    @Override
    public void register(StompClientConnection connection) {

        State current = state.get();
        if (current != State.RUNNING) {
            connection.close();
            return;
        }

        connections.put(connection.session(), connection);

        State afterRegister = state.get();
        if (afterRegister == State.CLOSING || afterRegister == State.CLOSED) {
            cleanUp(connection);
            connection.close();
        }
    }

    @Override
    public void unregister(String sessionId) {
        StompClientConnection connection = connections.get(sessionId);
        if (connection != null) {
            cleanUp(connection);
        }
    }

    @Override
    public void cleanUp(StompClientConnection connection) {
        if (state.get() == State.CLOSED) {
            return;
        }

        connections.remove(connection.session());

        subscriptions.unregisterAll(connection);

        Transactions.getInstance().removeTransactions(connection);
    }

    @Override
    public void cleanupInactiveConnections() {
        State current = state.get();
        if (current != State.RUNNING && current != State.CLOSING) {
            return;
        }

        connections.values().forEach(connection -> {
            Channel channel = connection.channel();
            if (channel.isClosed() || !channel.isActive()) {
                cleanUp(connection);
            }
        });
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
    public StompServerSubscriptions subscriptions() {
        return subscriptions;
    }

    @Override
    public Instant setLastActivatedAt() {
        return serverChannel.setLastActivatedAt();
    }

    @Override
    public Instant lastActivatedAt() {
        return serverChannel.lastActivatedAt();
    }

    @Override
    public String version() {
        return option.stompVersion().getVersion();
    }

    @Override
    public void close() {
        if (!state.compareAndSet(State.RUNNING, State.CLOSING)
                && !state.compareAndSet(State.INIT, State.CLOSING)) {
            return;
        }

        try {
            Collection<StompClientConnection> activeConnections = connections.values();
            activeConnections.forEach(this::cleanUp);
            activeConnections.forEach(StompClientConnection::close);
            connections.clear();
            serverChannel.close();
        } finally {
            state.set(State.CLOSED);
        }
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
        return !(channel.isClosed() || !channel.isActive());
    }

    private NetChannel asNetChannel(StompClientConnection connection) {
        return connection.channel();
    }

}
