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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.codec.stomp.Transactions;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.jspecify.annotations.Nullable;

/**
 * @author yungwang-o
 */
public class StompServerTcpChannel implements StompServerChannel {

    private final NetServerChannel serverChannel;
    private final StompServerOption option;
    private final Map<String, StompClientChannel> connections = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinSequence = new AtomicInteger();
    private final StompServerSubscriptions subscriptions = new StompServerSubscriptions();

    private final AtomicReference<State> state = new AtomicReference<>(State.INIT);

    enum State {
        INIT,
        RUNNING,
        CLOSING,
        CLOSED
    }

    StompServerTcpChannel(
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

    StompServerTcpChannel(NetServerChannel serverChannel, StompServerOption option) {
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
        StompClientChannel connection = nextConnection();
        if (connection == null) {
            return Promise.failedPromise(
                    serverChannel.eventLoop(),
                    new StompNetServeChannelException("No active STOMP client connections")
            );
        }

        NetChannel netChannel = asNetChannel(connection);
        return netChannel.writeAndFlush(buffer);
    }

    @Override
    public void register(StompClientChannel connection) {

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
        StompClientChannel connection = connections.get(sessionId);
        if (connection != null) {
            cleanUp(connection);
        }
    }

    @Override
    public void cleanUp(StompClientChannel connection) {
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
    public @Nullable StompClientChannel findConnection(String sessionId) {
        return connections.get(sessionId);
    }

    @Override
    public List<StompClientChannel> connections() {
        return List.copyOf(connections.values());
    }

    @Override
    public StompClientChannel connection() {
        StompClientChannel stompClientConnection = nextConnection();
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
            List<StompClientChannel> activeConnections = List.copyOf(connections.values());
            activeConnections.forEach(this::cleanUp);
            activeConnections.forEach(StompClientChannel::close);
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

    private @Nullable StompClientChannel nextConnection() {
        List<StompClientChannel> activeConnections = activeConnections();
        if (activeConnections.isEmpty()) {
            return null;
        }

        int index = Math.floorMod(roundRobinSequence.getAndIncrement(), activeConnections.size());
        return activeConnections.get(index);
    }

    private List<StompClientChannel> activeConnections() {
        return connections.values().stream()
                .filter(this::isActive)
                .toList();
    }

    private boolean isActive(StompClientChannel connection) {
        Channel channel = connection.channel();
        return !(channel.isClosed() || !channel.isActive());
    }

    private NetChannel asNetChannel(StompClientChannel connection) {
        return connection.channel();
    }

}
