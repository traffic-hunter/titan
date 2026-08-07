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
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompClient;
import io.vertx.ext.stomp.StompClientConnection;
import io.vertx.ext.stomp.StompClientOptions;
import io.vertx.ext.stomp.impl.FrameParser;
import io.vertx.ext.stomp.impl.StompClientConnectionImpl;
import io.vertx.ext.stomp.utils.Headers;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.transport.option.InetClientOption;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * STOMP client driver backed by Vert.x networking primitives.
 *
 * <p>The driver uses Vert.x STOMP directly for TCP connections and adapts a Vert.x WebSocket when
 * a WebSocket path is configured. Each successful attempt is exposed as a
 * {@link VertxStompConnection}; lifecycle state and reconnect scheduling remain in
 * {@link DefaultTitanClient}.</p>
 *
 * <p>The no-argument-runtime constructor creates and owns a Vert.x instance. Constructors and
 * factories receiving an existing Vert.x object preserve the caller-owned runtime on close.</p>
 *
 * @author yun
 */
public final class VertxStompClientDriver implements StompClientDriver {

    private final ClientConfiguration configuration;
    private final boolean managedVertx;
    private final Vertx vertx;
    private final Worker worker;
    private @Nullable StompClient client;
    private @Nullable WebSocketClient webSocketClient;
    private volatile @Nullable StompClientConnection connection;
    private volatile boolean started;
    private volatile boolean closed;

    /**
     * Creates a driver that creates and owns its Vert.x runtime.
     *
     * @param configuration immutable client and protocol configuration
     */
    public VertxStompClientDriver(ClientConfiguration configuration) {
        this(Vertx.vertx(), null, configuration, true);
    }

    /**
     * Creates a driver using a caller-owned Vert.x runtime.
     *
     * @param vertx runtime used for connections and callbacks
     * @param configuration immutable client and protocol configuration
     */
    public VertxStompClientDriver(Vertx vertx, ClientConfiguration configuration) {
        this(vertx, null, configuration, false);
    }

    private VertxStompClientDriver(
            Vertx vertx,
            @Nullable StompClient client,
            ClientConfiguration configuration,
            boolean managedVertx
    ) {
        this.vertx = vertx;
        this.client = client;
        this.configuration = configuration;
        this.managedVertx = managedVertx;
        this.worker = new VertxWorker(vertx.getOrCreateContext());
        this.started = client != null && !client.isClosed();
    }

    /**
     * Adapts an already-created, caller-owned Vert.x STOMP client.
     *
     * <p>The wrapped STOMP client is closed with this driver, while its underlying Vert.x runtime
     * remains owned by the caller.</p>
     *
     * @param client native Vert.x STOMP client to adapt
     * @param configuration configuration used by the common facade
     * @return a started driver around {@code client}
     */
    public static VertxStompClientDriver wrap(StompClient client, ClientConfiguration configuration) {
        return new VertxStompClientDriver(client.vertx(), client, configuration, false);
    }

    @Override
    public String name() {
        return "vertx";
    }

    @Override
    public void start() {
        if (closed) {
            throw new ClientException("Client driver is closed");
        }
        if (started) {
            return;
        }

        if (configuration.webSocketPath() == null) {
            client = StompClient.create(vertx, toVertxOptions(configuration));
        } else {
            webSocketClient = vertx.createWebSocketClient(toWebSocketOptions(configuration));
        }
        started = true;
    }

    @Override
    public ClientConfiguration clientConfiguration() {
        return configuration;
    }

    @Override
    public Worker worker() {
        return worker;
    }

    @Override
    public CompletableFuture<StompConnection> connect(InetSocketAddress remoteAddress) throws ClientException {
        if (!started || closed) {
            return CompletableFuture.failedFuture(new ClientException("Client driver is not started"));
        }

        StompClientConnection current = connection;
        if (current != null && current.isConnected()) {
            return CompletableFuture.failedFuture(new ClientException("STOMP client is already connected"));
        }

        io.vertx.core.Future<StompClientConnection> result;
        String path = configuration.webSocketPath();
        if (path == null) {
            StompClient client = this.client;
            if (client == null || client.isClosed()) {
                return CompletableFuture.failedFuture(new ClientException("Client driver is not started"));
            }
            result = client.connect(remoteAddress.getPort(), remoteAddress.getHostString());
        } else {
            result = connectWebSocket(remoteAddress, path);
        }

        return result.map(connection -> {
                    this.connection = connection;
                    return (StompConnection) new VertxStompConnection(connection);
                })
                .toCompletionStage()
                .toCompletableFuture();
    }

    @Override
    public void close(long timeout, TimeUnit unit) {
        if (closed) {
            return;
        }
        closed = true;
        started = false;

        try {
            StompClient client = this.client;
            if (client != null && !client.isClosed()) {
                client.close().await(timeout, unit);
            }
            WebSocketClient webSocketClient = this.webSocketClient;
            if (webSocketClient != null) {
                webSocketClient.shutdown(timeout, unit)
                        .await(timeout, unit);
            }
            if (managedVertx) {
                vertx.close().await(timeout, unit);
            }
        } catch (TimeoutException e) {
            throw new ClientException("Timed out closing Vert.x STOMP client driver", e);
        }
    }

    /**
     * Returns the Vert.x runtime used by this driver.
     *
     * <p>The return value does not transfer ownership. Runtime shutdown follows the constructor
     * ownership rules documented by this class.</p>
     *
     * @return driver runtime
     */
    public Vertx vertx() {
        return vertx;
    }

    @Nullable StompClientConnection channel() {
        return connection;
    }

    private io.vertx.core.Future<StompClientConnection> connectWebSocket(
            InetSocketAddress remoteAddress,
            String path
    ) {
        WebSocketClient client = this.webSocketClient;
        if (client == null) {
            return io.vertx.core.Future.failedFuture(new ClientException("Client driver is not started"));
        }

        WebSocketConnectOptions connectOptions = new WebSocketConnectOptions()
                .setHost(remoteAddress.getHostString())
                .setPort(remoteAddress.getPort())
                .setURI(path)
                .setConnectTimeout(configuration.connectTimeout().toMillis())
                .addSubProtocol("v12.stomp");

        return client.connect(connectOptions).compose(socket -> {
            VertxWebSocketNetSocket netSocket = new VertxWebSocketNetSocket(socket);
            StompClientOptions stompOptions = toVertxOptions(configuration);
            StompClientConnectionImpl connection = new StompClientConnectionImpl(
                    (ContextInternal) vertx.getOrCreateContext(),
                    netSocket,
                    stompOptions
            );

            netSocket.write(connectFrame(stompOptions).toBuffer(stompOptions.isTrailingLine()));
            long timer = vertx.setTimer(configuration.connectTimeout().toMillis(), ignored -> {
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

    private static StompClientOptions toVertxOptions(ClientConfiguration configuration) {
        StompClientOptions options = new StompClientOptions()
                .setHost(configuration.host())
                .setPort(configuration.port())
                .setAcceptedVersions(List.of(configuration.stompVersion().getVersion()))
                .setAutoComputeContentLength(configuration.autoComputeContentLength())
                .setUseStompFrame(configuration.useStompFrame())
                .setBypassHostHeader(configuration.bypassHostHeader())
                .setHeartbeat(new JsonObject()
                        .put("x", configuration.heartbeatX())
                        .put("y", configuration.heartbeatY()));
        options.setConnectTimeout(Math.toIntExact(configuration.connectTimeout().toMillis()));
        if (configuration.login() != null) {
            options.setLogin(configuration.login());
        }
        if (configuration.passcode() != null) {
            options.setPasscode(configuration.passcode());
        }
        if (configuration.virtualHost() != null) {
            options.setVirtualHost(configuration.virtualHost());
        }
        applyInetOptions(options, configuration.inetClientOption());
        return options;
    }

    private static WebSocketClientOptions toWebSocketOptions(ClientConfiguration configuration) {
        WebSocketClientOptions options = new WebSocketClientOptions()
                .setDefaultHost(configuration.host())
                .setDefaultPort(configuration.port())
                .setConnectTimeout(Math.toIntExact(configuration.connectTimeout().toMillis()))
                .setMaxMessageSize(configuration.maxFrameLength());
        applyInetOptions(options, configuration.inetClientOption());
        return options;
    }

    private static void applyInetOptions(StompClientOptions options, InetClientOption configuration) {
        Map<SocketOption<?>, Object> socketOptions = configuration.socketOptions();
        applyBoolean(socketOptions, StandardSocketOptions.TCP_NODELAY, options::setTcpNoDelay);
        applyBoolean(socketOptions, StandardSocketOptions.SO_KEEPALIVE, options::setTcpKeepAlive);
        applyBoolean(socketOptions, StandardSocketOptions.SO_REUSEADDR, options::setReuseAddress);
        applyInteger(socketOptions, StandardSocketOptions.SO_SNDBUF, options::setSendBufferSize);
        applyInteger(socketOptions, StandardSocketOptions.SO_RCVBUF, options::setReceiveBufferSize);
        applyInteger(socketOptions, StandardSocketOptions.SO_LINGER, options::setSoLinger);
    }

    private static void applyInetOptions(WebSocketClientOptions options, InetClientOption configuration) {
        Map<SocketOption<?>, Object> socketOptions = configuration.socketOptions();
        applyBoolean(socketOptions, StandardSocketOptions.TCP_NODELAY, options::setTcpNoDelay);
        applyBoolean(socketOptions, StandardSocketOptions.SO_KEEPALIVE, options::setTcpKeepAlive);
        applyBoolean(socketOptions, StandardSocketOptions.SO_REUSEADDR, options::setReuseAddress);
        applyInteger(socketOptions, StandardSocketOptions.SO_SNDBUF, options::setSendBufferSize);
        applyInteger(socketOptions, StandardSocketOptions.SO_RCVBUF, options::setReceiveBufferSize);
        applyInteger(socketOptions, StandardSocketOptions.SO_LINGER, options::setSoLinger);
    }

    private static void applyBoolean(
            Map<SocketOption<?>, Object> socketOptions,
            SocketOption<Boolean> option,
            BooleanOptionSetter setter
    ) {
        Object value = socketOptions.get(option);
        if (value instanceof Boolean valueToSet) {
            setter.set(valueToSet);
        }
    }

    private static void applyInteger(
            Map<SocketOption<?>, Object> socketOptions,
            SocketOption<Integer> option,
            IntegerOptionSetter setter
    ) {
        Object value = socketOptions.get(option);
        if (value instanceof Integer valueToSet) {
            setter.set(valueToSet);
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
