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
package org.traffichunter.titan.client;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompClient;
import io.vertx.ext.stomp.StompClientOptions;
import io.vertx.ext.stomp.StompClientConnection;
import io.vertx.ext.stomp.impl.FrameParser;
import io.vertx.ext.stomp.impl.StompClientConnectionImpl;
import io.vertx.ext.stomp.utils.Headers;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.resilience.retry.RetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryExecutors;
import org.traffichunter.titan.core.resilience.retry.RetryResult;
import org.traffichunter.titan.core.transport.option.InetClientOption;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Vert.x implementation hidden behind the public {@link TitanClient} facade.
 *
 * <p>Vert.x futures, STOMP frames, TCP sockets, and WebSocket streams are adapted at this
 * boundary. When this class creates the Vert.x instance, {@code managedVertx} is true and
 * shutdown closes it; externally supplied Vert.x resources remain owned by their caller.</p>
 *
 * @author yun
 */
final class VertxStompClient extends AbstractTitanClient {

    private final ClientConfiguration option;
    private final boolean managedVertx;

    private final RetryExecutor reconnectExecutor;
    private final AtomicReference<Status> status;
    private final AtomicReference<@Nullable RetryResult> reconnectResult = new AtomicReference<>();

    private @Nullable Vertx vertx;
    private @Nullable StompClient client;
    private @Nullable WebSocketClient webSocketClient;
    private final @Nullable String webSocketPath;
    private volatile @Nullable StompClientConnection connection;
    private volatile @Nullable VertxStompConnection stompConnection;
    private volatile boolean isShutdown;

    private VertxStompClient(
            @Nullable Vertx vertx,
            @Nullable StompClient client,
            ClientConfiguration option,
            boolean managedVertx
    ) {
        this.vertx = vertx;
        this.client = client;
        this.option = option;
        this.webSocketPath = option.webSocketPath();
        this.managedVertx = managedVertx;
        this.reconnectExecutor = RetryExecutors.vertxRetryExecutor(
                option.reconnectPolicy(),
                option.reconnectListener()
        );
        this.status = new AtomicReference<>(
                client != null && !client.isClosed() ? Status.STARTED : Status.INITIALIZED
        );
    }

    /** Creates a client that owns its Vert.x runtime and native STOMP client. */
    public static VertxStompClient open(ClientConfiguration option) {
        return new VertxStompClient(null, null, option, true);
    }

    /** Creates a client using a caller-owned Vert.x runtime. */
    public static VertxStompClient open(Vertx vertx, ClientConfiguration option) {
        return new VertxStompClient(vertx, null, option, false);
    }

    /** Wraps a caller-owned Vert.x STOMP client without taking runtime ownership. */
    public static VertxStompClient wrap(StompClient client, ClientConfiguration option) {
        return new VertxStompClient(client.vertx(), client, option, false);
    }

    @Override
    public String name() {
        return "vertx";
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
            if (webSocketPath == null) {
                client = StompClient.create(vertx, toVertxOptions(option));
            } else {
                webSocketClient = vertx.createWebSocketClient(toWebSocketOptions(option));
            }
            status.set(Status.STARTED);
        } catch (RuntimeException e) {
            status.set(Status.INITIALIZED);
            throw e;
        }
    }

    @Override
    protected CompletableFuture<StompConnection> connectConnection() {
        if (!status.compareAndSet(Status.STARTED, Status.CONNECTING)) {
            return CompletableFuture.failedFuture(
                    new StompException(status.get() == Status.INITIALIZED
                            ? "Client is not started"
                            : "STOMP client is not ready to connect")
            );
        }

        io.vertx.core.Future<StompConnection> result = connectStompConnection();
        result.onFailure(error -> status.compareAndSet(Status.CONNECTING, Status.STARTED));
        return result.toCompletionStage().toCompletableFuture();
    }

    private io.vertx.core.Future<StompConnection> connectStompConnection() {
        StompClientConnection connection = this.connection;
        if (connection != null && connection.isConnected()) {
            return io.vertx.core.Future.failedFuture(
                    new StompException("STOMP client is already connected")
            );
        }

        io.vertx.core.Future<StompClientConnection> connectionFuture;
        String path = webSocketPath;
        if (path == null) {
            StompClient nativeClient = this.client;
            if (nativeClient == null || nativeClient.isClosed()) {
                return io.vertx.core.Future.failedFuture(new StompException("Client is not started"));
            }
            connectionFuture = nativeClient.connect(option.port(), option.host());
        } else {
            connectionFuture = connectWebSocket(path);
        }

        return connectionFuture
                .map(conn -> {
                    if (!status.compareAndSet(Status.CONNECTING, Status.CONNECTED)) {
                        conn.close();
                        throw new StompException("STOMP client stopped while connecting");
                    }
                    this.connection = conn;
                    return bindStompConnection(conn);
                });
    }

    @Override
    StompConnection connection() {
        VertxStompConnection stompConnection = this.stompConnection;
        if (stompConnection == null) {
            throw new IllegalStateException("STOMP client is not connected");
        }
        return stompConnection;
    }

    @Override
    public boolean isStarted() {
        StompClient client = this.client;
        WebSocketClient webSocketClient = this.webSocketClient;
        return client != null && !client.isClosed() || webSocketClient != null && !isShutdown;
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
            WebSocketClient webSocketClient = this.webSocketClient;
            if (webSocketClient != null) {
                webSocketClient.shutdown(timeout, unit).await(timeout, unit);
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

    public ClientConfiguration option() {
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

    private VertxStompConnection bindStompConnection(StompClientConnection connection) {
        VertxStompConnection stompConnection = this.stompConnection;
        if (stompConnection == null) {
            stompConnection = createStompConnection(connection);
            this.stompConnection = stompConnection;
        } else {
            stompConnection.replace(connection);
        }
        return stompConnection;
    }

    private io.vertx.core.Future<StompClientConnection> connectWebSocket(String path) {
        Vertx vertx = this.vertx;
        WebSocketClient client = this.webSocketClient;
        if (vertx == null || client == null) {
            return io.vertx.core.Future.failedFuture(new StompException("Client is not started"));
        }

        WebSocketConnectOptions connectOptions = new WebSocketConnectOptions()
                .setHost(option.host())
                .setPort(option.port())
                .setURI(path)
                .setConnectTimeout(option.connectTimeout().toMillis())
                .addSubProtocol("v12.stomp");

        return client.connect(connectOptions).compose(socket -> {
            VertxWebSocketNetSocket netSocket = new VertxWebSocketNetSocket(socket);
            StompClientOptions stompOptions = toVertxOptions(option);
            StompClientConnectionImpl connection = new StompClientConnectionImpl(
                    (ContextInternal) vertx.getOrCreateContext(),
                    netSocket,
                    stompOptions
            );

            netSocket.write(connectFrame(stompOptions).toBuffer(stompOptions.isTrailingLine()));
            long timer = vertx.setTimer(option.connectTimeout().toMillis(), ignored -> {
                if (!connection.isConnected()) {
                    connection.close();
                }
            });
            return connection.connectFuture()
                    .map(ignored -> (StompClientConnection) connection)
                    .eventually(() -> {
                        vertx.cancelTimer(timer);
                        return io.vertx.core.Future.succeededFuture();
                    });
        });
    }

    private static Frame connectFrame(StompClientOptions options) {
        Headers headers = Headers.create();
        if (options.getAcceptedVersions() != null && !options.getAcceptedVersions().isEmpty()) {
            headers.put(Frame.ACCEPT_VERSION, String.join(FrameParser.COMMA, options.getAcceptedVersions()));
        }
        if (!options.isBypassHostHeader()) {
            headers.put(Frame.HOST, options.getHost());
        }
        if (options.getVirtualHost() != null) {
            headers.put(Frame.HOST, options.getVirtualHost());
        }
        if (options.getLogin() != null) {
            headers.put(Frame.LOGIN, options.getLogin());
        }
        if (options.getPasscode() != null) {
            headers.put(Frame.PASSCODE, options.getPasscode());
        }
        headers.put(Frame.HEARTBEAT, Frame.Heartbeat.create(options.getHeartbeat()).toString());
        return new Frame(options.isUseStompFrame() ? Command.STOMP : Command.CONNECT, headers, null);
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
                Throwable cause = e.getCause();
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

    private static StompClientOptions toVertxOptions(ClientConfiguration option) {
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

    private static WebSocketClientOptions toWebSocketOptions(ClientConfiguration option) {
        WebSocketClientOptions options = new WebSocketClientOptions()
                .setDefaultHost(option.host())
                .setDefaultPort(option.port())
                .setConnectTimeout(Math.toIntExact(option.connectTimeout().toMillis()))
                .setMaxMessageSize(option.maxFrameLength());
        applyInetOptions(options, option.inetClientOption());
        return options;
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

    private static void applyInetOptions(WebSocketClientOptions vertxOptions, InetClientOption option) {
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

    @FunctionalInterface
    private interface BooleanOptionSetter {
        void set(boolean value);
    }

    @FunctionalInterface
    private interface IntegerOptionSetter {
        void set(int value);
    }
}
