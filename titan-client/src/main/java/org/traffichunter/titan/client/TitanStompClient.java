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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientHandler;
import org.traffichunter.titan.core.channel.stomp.StompNetChannelException;
import org.traffichunter.titan.core.codec.stomp.*;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.resilience.retry.RetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryExecutors;
import org.traffichunter.titan.core.resilience.retry.RetryResult;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.websocket.WebSocketClient;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.Protocol;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.HeartBeat;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

/**
 * Titan native implementation of the public {@link TitanClient} facade.
 *
 * <p>The client owns an {@link InetClient}, performs STOMP negotiation on Titan event loops, and
 * coordinates unlimited reconnect attempts through the configured retry executor. A stable
 * {@link TitanStompConnection} facade survives reconnect while its underlying
 * {@link StompClientChannel} is replaced.</p>
 *
 * @author yun
 */
@Slf4j
final class TitanStompClient extends AbstractTitanClient {

    private final InetClient inetClient;
    private final ClientConfiguration option;
    private final RetryExecutor reconnectExecutor;
    private final AtomicReference<Status> status;

    private Handler<StompClientHandler> stompClientHandler = handler -> {};
    private final @Nullable String webSocketPath;
    private volatile @Nullable StompClientChannel connection;
    private volatile @Nullable TitanStompConnection stompConnection;
    private final AtomicReference<@Nullable RetryResult> reconnectResult = new AtomicReference<>();

    private TitanStompClient(EventLoopGroups groups, @Nullable InetClient inetClient, ClientConfiguration option) {
        this.option = option;
        this.webSocketPath = option.webSocketPath();

        if (inetClient == null) {
            inetClient = InetClient.open(groups, option.inetClientOption());
        }
        this.inetClient = inetClient;
        TlsContext tlsContext = option.tlsContext();
        if (tlsContext != null) {
            inetClient.tls(tlsContext);
        }
        this.reconnectExecutor = RetryExecutors.eventLoopRetryExecutor(
                option.reconnectPolicy(),
                option.reconnectListener()
        );
        this.status = new AtomicReference<>(
                inetClient.isStarted() ? Status.STARTED : Status.INITIALIZED
        );
    }

    /** Creates a native client that owns a newly created {@link InetClient}. */
    public static TitanStompClient open(EventLoopGroups groups, ClientConfiguration option) {
        return open(groups, null, option);
    }

    /** Creates a native client around an optional preconfigured transport, primarily for tests. */
    public static TitanStompClient open(EventLoopGroups groups, @Nullable InetClient inetClient, ClientConfiguration option) {
        return new TitanStompClient(groups, inetClient, option);
    }

    @Override
    StompConnection connection() {
        TitanStompConnection stompConnection = this.stompConnection;
        if (stompConnection == null) {
            throw new IllegalStateException("STOMP client is not connected");
        }
        return stompConnection;
    }

    @Override
    public boolean isShutdown() {
        return inetClient.isShutdown();
    }

    @CanIgnoreReturnValue
    public TitanStompClient onChannel(Handler<Channel> channelHandler) {
        inetClient.onChannel(channelHandler);
        return this;
    }

    @CanIgnoreReturnValue
    public TitanStompClient onStomp(Handler<StompClientHandler> stompClientHandler) {
        this.stompClientHandler = stompClientHandler;
        return this;
    }

    @Override
    public String name() {
        return "titan";
    }

    public void start() {
        if (!status.compareAndSet(Status.INITIALIZED, Status.STARTING)) {
            throw new StompException("Client already started");
        }

        try {
            inetClient.start();
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

        Promise<StompConnection> result = connectStompConnection()
                .addListener(future -> {
                    if (future.isFailed()) {
                        status.compareAndSet(Status.CONNECTING, Status.STARTED);
                    }
                });

        return result.toCompletableFuture();
    }

    public Promise<StompClientChannel> connect(String host, int port) {
        return connect(
                host,
                port,
                option.connectTimeout().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    public Promise<StompClientChannel> connect(String host, int port, long timeOut, TimeUnit timeUnit) {
        return connect(new InetSocketAddress(host, port), timeOut, timeUnit);
    }

    public Promise<StompClientChannel> connect(InetSocketAddress remoteAddress, long timeOut, TimeUnit timeUnit) {
        if (connection != null && connection.isConnected()) {
            return Promise.failedPromise(connection.channel().eventLoop(), new StompException("STOMP client is already connected"));
        }

        return connectTransport(remoteAddress, timeOut, timeUnit)
                .map(this::createConnection)
                .thenCompose(conn -> {
                    StompFrame connectFrame = generateConnectFrame(remoteAddress.getHostString());
                    return conn.send(connectFrame)
                            .thenCompose(frame -> awaitConnected(conn, remoteAddress, timeOut, timeUnit));
                }).onFailure(error -> log.error("Failed to connect to {}", remoteAddress, error));
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    public void shutdown(long timeout, TimeUnit unit) {
        if (!transitionToShuttingDown()) {
            return;
        }

        cancelReconnect();
        try {
            reconnectExecutor.shutdown(timeout, unit);
            inetClient.shutdown(timeout, unit);
        } finally {
            status.set(Status.SHUTDOWN);
        }
    }

    public @Nullable SocketAddress remoteAddress() {
        return inetClient.remoteAddress();
    }

    public boolean isStarted() {
        return inetClient.isStarted();
    }

    public boolean isClosed() {
        return inetClient.isClosed();
    }

    public String version() {
        return StompVersion.STOMP_1_2.getVersion();
    }

    public ClientConfiguration option() {
        return option;
    }

    public StompClientChannel channel() {
        StompClientChannel connection = this.connection;
        if (connection == null) {
            throw new IllegalStateException("STOMP client is not connected");
        }
        return connection;
    }

    public void handler(Consumer<StompClientChannel> connection) {
        connection.accept(channel());
    }

    private StompClientChannel createConnection(NetChannel channel) {
        StompClientChannel connection = channel instanceof WebSocketChannel webSocketChannel
                ? StompClientChannel.wrap(webSocketChannel, option.session())
                : StompClientChannel.wrap(channel, option.session());
        stompClientHandler.handle(connection.handler());
        channel.chain().add(new StompChannelDecoder(option.maxFrameLength(), connection, connection.handler()));
        this.connection = connection;
        return connection;
    }

    private Promise<? extends NetChannel> connectTransport(
            InetSocketAddress remoteAddress,
            long timeOut,
            TimeUnit timeUnit
    ) {
        String path = webSocketPath;
        if (path == null) {
            return inetClient.connect(remoteAddress, timeOut, timeUnit);
        }

        WebSocketClient webSocketClient = inetClient.upgradeWebSocket(Protocol.STOMP, path);
        return webSocketClient.connect(remoteAddress, timeOut, timeUnit);
    }

    private Promise<StompConnection> connectStompConnection() {
        return connect(option.host(), option.port())
                .map(connection -> {
                    if (!status.compareAndSet(Status.CONNECTING, Status.CONNECTED)) {
                        connection.close();
                        throw new StompException("STOMP client stopped while connecting");
                    }
                    return bindStompConnection(connection);
                });
    }

    private TitanStompConnection bindStompConnection(StompClientChannel connection) {
        TitanStompConnection stompConnection = this.stompConnection;
        if (stompConnection == null) {
            stompConnection = createStompConnection(connection);
            this.stompConnection = stompConnection;
        } else {
            stompConnection.replace(connection);
        }
        return stompConnection;
    }

    private TitanStompConnection createStompConnection(StompClientChannel connection) {
        return new TitanStompConnection(
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
                connectStompConnection().get();
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

    private void cancelReconnect() {
        RetryResult result = reconnectResult.getAndSet(null);
        if (result != null) {
            result.cancel();
        }
    }

    private void disconnecting() {
        cancelReconnect();
        status.compareAndSet(Status.CONNECTED, Status.STARTED);
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

    private StompFrame generateConnectFrame(String host) {
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ACCEPT_VERSION, option.stompVersion().getVersion());

        if (!option.bypassHostHeader()) {
            headers.put(Elements.HOST, host);
        }
        if (option.virtualHost() != null) {
            headers.put(Elements.HOST, option.virtualHost());
        }
        if (option.login() != null) {
            headers.put(Elements.LOGIN, option.login());
        }
        if (option.passcode() != null) {
            headers.put(Elements.PASSCODE, option.passcode());
        }

        headers.put(Elements.HEART_BEAT, HeartBeat.create(option.heartbeatX(), option.heartbeatY()).value());
        StompCommand command = option.useStompFrame() ? StompCommand.STOMP : StompCommand.CONNECT;
        return StompFrame.create(headers, command);
    }

    private Promise<StompClientChannel> awaitConnected(
            StompClientChannel conn,
            InetSocketAddress remoteAddress,
            long timeOut,
            TimeUnit timeUnit
    ) {
        Promise<StompClientChannel> result = Promise.newPromise(conn.channel().eventLoop());
        ScheduledPromise<Object> timeoutTask = conn.channel().eventLoop().schedule(() -> {
            if (result.tryFail(new StompNetChannelException("Timed out waiting for CONNECTED from " + remoteAddress + " in " + timeOut + " " + timeUnit))) {
                conn.close();
            }
        }, timeOut, timeUnit);

        conn.connectedPromise().addListener(connectedFuture -> {
            timeoutTask.cancel();
            if (connectedFuture.isSuccess()) {
                log.info(
                        "Connected STOMP client. remoteAddress={}, session={}, version={}, heartbeat={}",
                        remoteAddress,
                        conn.session(),
                        option.stompVersion().getVersion(),
                        HeartBeat.create(option.heartbeatX(), option.heartbeatY()).value()
                );
                result.trySuccess(conn);
                return;
            }

            Throwable error = connectedFuture.error();
            if (error != null) {
                result.tryFail(error);
            } else {
                result.tryFail(new StompNetChannelException("STOMP connect failed without error cause"));
            }
        });

        return result;
    }
}
