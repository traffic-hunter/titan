/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.transport.stomp;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.stomp.StompClient;
import io.vertx.ext.stomp.StompClientOptions;
import io.vertx.ext.stomp.StompClientConnection;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.resilience.retry.RetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryExecutors;
import org.traffichunter.titan.core.resilience.retry.RetryResult;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.transport.stomp.client.StompConnection;
import org.traffichunter.titan.core.transport.stomp.client.VertxStompConnection;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yun
 */
public final class VertxStompClient implements org.traffichunter.titan.core.transport.stomp.client.StompClient {

    private final StompClientOption option;
    private final boolean managedVertx;

    private final RetryExecutor reconnectExecutor;
    private final AtomicReference<Status> status;
    private final AtomicReference<@Nullable RetryResult> reconnectResult = new AtomicReference<>();

    private @Nullable Vertx vertx;
    private @Nullable StompClient client;
    private volatile @Nullable StompClientConnection connection;
    private volatile @Nullable VertxStompConnection stompConnection;
    private volatile boolean isShutdown;

    private VertxStompClient(
            @Nullable Vertx vertx,
            @Nullable StompClient client,
            StompClientOption option,
            boolean managedVertx
    ) {
        this.vertx = vertx;
        this.client = client;
        this.option = option;
        this.managedVertx = managedVertx;
        this.reconnectExecutor = RetryExecutors.vertxRetryExecutor(
                option.reconnectPolicy(),
                option.reconnectListener()
        );
        this.status = new AtomicReference<>(
                client != null && !client.isClosed() ? Status.STARTED : Status.INITIALIZED
        );
    }

    public static VertxStompClient open(StompClientOption option) {
        return new VertxStompClient(null, null, option, true);
    }

    public static VertxStompClient open(Vertx vertx, StompClientOption option) {
        return new VertxStompClient(vertx, null, option, false);
    }

    public static VertxStompClient wrap(StompClient client, StompClientOption option) {
        return new VertxStompClient(client.vertx(), client, option, false);
    }

    @Override
    public void start() {
        if (isShutdown) {
            throw new StompException("Client has been shut down");
        }
        if (!status.compareAndSet(Status.INITIALIZED, Status.STARTING)) {
            throw new StompException("Client already started");
        }

        try {
            Vertx vertx = this.vertx;
            if (vertx == null) {
                vertx = Vertx.vertx();
                this.vertx = vertx;
            }
            client = StompClient.create(vertx, toVertxOptions(option));
            status.set(Status.STARTED);
        } catch (RuntimeException e) {
            status.set(Status.INITIALIZED);
            throw e;
        }
    }

    @Override
    public Future<StompConnection> connect() {
        if (!status.compareAndSet(Status.STARTED, Status.CONNECTING)) {
            return CompletableFuture.failedFuture(
                    new StompException(status.get() == Status.INITIALIZED
                            ? "Client is not started"
                            : "STOMP client is not ready to connect")
            );
        }

        io.vertx.core.Future<StompConnection> result = connectStompConnection();
        result.onFailure(error -> status.compareAndSet(Status.CONNECTING, Status.STARTED));
        return VertxFutureWrapper.wrap(result);
    }

    private io.vertx.core.Future<StompConnection> connectStompConnection() {
        StompClientConnection connection = this.connection;
        if (connection != null && connection.isConnected()) {
            return io.vertx.core.Future.failedFuture(
                    new StompException("STOMP client is already connected")
            );
        }

        StompClient nativeClient = this.client;
        if (nativeClient == null || nativeClient.isClosed()) {
            return io.vertx.core.Future.failedFuture(new StompException("Client is not started"));
        }

        return nativeClient
                .connect(option.port(), option.host())
                .map(conn -> {
                    VertxStompConnection stompConnection = createStompConnection(conn);
                    if (!status.compareAndSet(Status.CONNECTING, Status.CONNECTED)) {
                        conn.close();
                        throw new StompException("STOMP client stopped while connecting");
                    }
                    this.connection = conn;
                    this.stompConnection = stompConnection;
                    return stompConnection;
                });
    }

    @Override
    public StompConnection connection() {
        VertxStompConnection stompConnection = this.stompConnection;
        if (stompConnection == null) {
            StompClientConnection connection = this.connection;
            if (connection == null) {
                throw new IllegalStateException("STOMP client is not connected");
            }
            stompConnection = createStompConnection(connection);
            this.stompConnection = stompConnection;
        }
        return stompConnection;
    }

    @Override
    public boolean isStarted() {
        return client != null && !client.isClosed();
    }

    @Override
    public boolean isShutdown() {
        StompClient client = this.client;
        return isShutdown || client != null && client.isClosed();
    }

    @Override
    public void shutdown(long timeout, TimeUnit unit) {
        if (!transitionToShuttingDown()) {
            return;
        }

        cancelReconnect();
        StompClient client = this.client;
        try {
            reconnectExecutor.shutdown(timeout, unit);
            if (client != null && !client.isClosed()) {
                client.close().await(timeout, unit);
            }
            Vertx vertx = this.vertx;
            if (managedVertx && vertx != null) {
                vertx.close().await(timeout, unit);
            }
        } catch (TimeoutException e) {
            throw new StompException("Timed out shutting down Vert.x STOMP client", e);
        } finally {
            isShutdown = true;
            status.set(Status.SHUTDOWN);
        }
    }

    public StompClientOption option() {
        return option;
    }

    public StompClient client() {
        StompClient client = this.client;
        if (client == null || client.isClosed()) {
            throw new IllegalStateException("STOMP client is not started");
        }
        return client;
    }

    public StompClientConnection channel() {
        StompClientConnection connection = this.connection;
        if (connection == null) {
            throw new IllegalStateException("STOMP client is not connected");
        }
        return connection;
    }

    private VertxStompConnection createStompConnection(StompClientConnection connection) {
        return new VertxStompConnection(
                connection,
                this::disconnecting,
                ignored -> connectionLost(),
                ignored -> connectionLost()
        );
    }

    private void connectionLost() {
        if (!status.compareAndSet(Status.CONNECTED, Status.CONNECTING)) {
            return;
        }

        RetryResult result = reconnectExecutor.retry(() -> {
            if (status.get() != Status.CONNECTING) {
                return Boolean.TRUE;
            }

            try {
                connectStompConnection().toCompletionStage().toCompletableFuture().get();
            } catch (ExecutionException e) {
                @Nullable Throwable cause = e.getCause();
                if (cause instanceof Exception retryable) {
                    throw retryable;
                }
                throw cause == null
                        ? new StompException("STOMP reconnect failed")
                        : new StompException("STOMP reconnect failed", cause);
            }
            return Boolean.TRUE;
        });
        RetryResult previous = reconnectResult.getAndSet(result);
        if (previous != null) {
            previous.cancel();
        }
        if (status.get() != Status.CONNECTING && reconnectResult.compareAndSet(result, null)) {
            result.cancel();
        }
    }

    private void disconnecting() {
        cancelReconnect();
        status.compareAndSet(Status.CONNECTED, Status.STARTED);
    }

    private void cancelReconnect() {
        RetryResult result = reconnectResult.getAndSet(null);
        if (result != null) {
            result.cancel();
        }
    }

    private boolean transitionToShuttingDown() {
        while (true) {
            Status current = status.get();
            if (current == Status.SHUTTING_DOWN || current == Status.SHUTDOWN) {
                return false;
            }
            if (status.compareAndSet(current, Status.SHUTTING_DOWN)) {
                return true;
            }
        }
    }

    private static StompClientOptions toVertxOptions(StompClientOption option) {
        StompClientOptions vertxOptions = new StompClientOptions()
                .setHost(option.host())
                .setPort(option.port())
                .setAcceptedVersions(List.of(option.stompVersion().getVersion()))
                .setAutoComputeContentLength(option.autoComputeContentLength())
                .setUseStompFrame(option.useStompFrame())
                .setBypassHostHeader(option.bypassHostHeader())
                .setHeartbeat(new JsonObject()
                        .put("x", option.heartbeatX())
                        .put("y", option.heartbeatY())
                );
        vertxOptions.setConnectTimeout(Math.toIntExact(option.connectTimeout().toMillis()));

        if (option.login() != null) {
            vertxOptions.setLogin(option.login());
        }
        if (option.passcode() != null) {
            vertxOptions.setPasscode(option.passcode());
        }
        if (option.virtualHost() != null) {
            vertxOptions.setVirtualHost(option.virtualHost());
        }

        applyInetOptions(vertxOptions, option.inetClientOption());
        return vertxOptions;
    }

    private static void applyInetOptions(StompClientOptions vertxOptions, InetClientOption option) {
        Map<SocketOption<?>, Object> socketOptions = option.socketOptions();
        applyBoolean(socketOptions, StandardSocketOptions.TCP_NODELAY, vertxOptions::setTcpNoDelay);
        applyBoolean(socketOptions, StandardSocketOptions.SO_KEEPALIVE, vertxOptions::setTcpKeepAlive);
        applyBoolean(socketOptions, StandardSocketOptions.SO_REUSEADDR, vertxOptions::setReuseAddress);
        applyInteger(socketOptions, StandardSocketOptions.SO_SNDBUF, vertxOptions::setSendBufferSize);
        applyInteger(socketOptions, StandardSocketOptions.SO_RCVBUF, vertxOptions::setReceiveBufferSize);
        applyInteger(socketOptions, StandardSocketOptions.SO_LINGER, vertxOptions::setSoLinger);
    }

    private static void applyBoolean(
            Map<SocketOption<?>, Object> socketOptions,
            SocketOption<Boolean> option,
            BooleanOptionSetter setter
    ) {
        Object value = socketOptions.get(option);
        if (value instanceof Boolean bool) {
            setter.set(bool);
        }
    }

    private static void applyInteger(
            Map<SocketOption<?>, Object> socketOptions,
            SocketOption<Integer> option,
            IntegerOptionSetter setter
    ) {
        Object value = socketOptions.get(option);
        if (value instanceof Integer number) {
            setter.set(number);
        }
    }

    private record VertxFutureWrapper<V>(CompletableFuture<V> future) implements Future<V> {

        private VertxFutureWrapper(io.vertx.core.Future<V> future) {
            this(future.toCompletionStage().toCompletableFuture());
        }

        static <V> Future<V> wrap(io.vertx.core.Future<V> future) {
            return new VertxFutureWrapper<>(future);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(timeout, unit);
        }
    }

    @FunctionalInterface
    private interface BooleanOptionSetter {
        void set(boolean value);
    }

    @FunctionalInterface
    private interface IntegerOptionSetter {
        void set(int value);
    }
}
