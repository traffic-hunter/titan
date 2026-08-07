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
import java.util.concurrent.TimeUnit;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientHandler;
import org.traffichunter.titan.core.channel.stomp.StompNetChannelException;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompChannelDecoder;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.websocket.WebSocketClient;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.Protocol;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.HeartBeat;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

/**
 * STOMP client driver backed by Titan's native {@link InetClient} and event loops.
 *
 * <p>The driver configures optional TLS and WebSocket transport before installing the STOMP
 * decoder. A successful connection attempt includes the STOMP CONNECT/CONNECTED exchange and
 * returns a {@link TitanStompConnection} adapter for the negotiated channel. Reconnect scheduling
 * remains outside this class.</p>
 *
 * @author yun
 */
public final class TitanStompClientDriver implements StompClientDriver {

    private static final Logger log = LoggerFactory.getLogger(TitanStompClientDriver.class);

    private final InetClient inetClient;
    private final ClientConfiguration configuration;
    private final Worker worker;
    private Handler<StompClientHandler> stompClientHandler = handler -> {};
    private volatile @Nullable StompClientChannel connection;

    /**
     * Creates a driver that owns an {@link InetClient} backed by the supplied event-loop groups.
     *
     * @param groups event-loop groups used by the native transport
     * @param configuration immutable client and protocol configuration
     */
    public TitanStompClientDriver(EventLoopGroups groups, ClientConfiguration configuration) {
        this(groups, null, configuration);
    }

    TitanStompClientDriver(
            EventLoopGroups groups,
            @Nullable InetClient inetClient,
            ClientConfiguration configuration
    ) {
        this.configuration = configuration;
        this.worker = new TitanWorker(groups.secondaryGroup().next());
        this.inetClient = inetClient == null
                ? InetClient.open(groups, configuration.inetClientOption())
                : inetClient;

        TlsContext tlsContext = configuration.tlsContext();
        if (tlsContext != null) {
            this.inetClient.tls(tlsContext);
        }
    }

    @Override
    public String name() {
        return "titan";
    }

    @Override
    public void start() {
        if (!inetClient.isStarted()) {
            inetClient.start();
        }
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
        if (!inetClient.isStarted()) {
            return CompletableFuture.failedFuture(new ClientException("Client driver is not started"));
        }

        StompClientChannel current = connection;
        if (current != null && current.isConnected()) {
            return CompletableFuture.failedFuture(new ClientException("STOMP client is already connected"));
        }

        long timeout = configuration.connectTimeout().toMillis();
        StompFrame connectFrame = createConnectFrame(remoteAddress.getHostString());
        return connectTransport(remoteAddress, timeout)
                .map(channel -> {
                    StompClientChannel connection = channel instanceof WebSocketChannel webSocketChannel
                            ? StompClientChannel.wrap(webSocketChannel, configuration.session())
                            : StompClientChannel.wrap(channel, configuration.session());
                    stompClientHandler.handle(connection.handler());
                    channel.chain().add(new StompChannelDecoder(
                            configuration.maxFrameLength(),
                            connection,
                            connection.handler()
                    ));
                    return connection;
                })
                .thenCompose(connection -> connection.send(connectFrame).map(ignored -> connection))
                .thenCompose(connection -> awaitConnected(connection, remoteAddress, timeout))
                .map(connection -> {
                    this.connection = connection;
                    return (StompConnection) new TitanStompConnection(connection);
                })
                .onFailure(error -> log.error("Failed to connect to {}", remoteAddress, error))
                .toCompletableFuture();
    }

    @Override
    public void close(long timeOut, TimeUnit timeUnit) {
        StompClientChannel connection = this.connection;
        if (connection != null && connection.isConnected()) {
            connection.close();
        }
        if (!inetClient.isShutdown()) {
            inetClient.shutdown(timeOut, timeUnit);
        }
    }

    /**
     * Closes the native client using the driver's default graceful-shutdown timeout.
     */
    public void close() {
        close(30, TimeUnit.SECONDS);
    }

    @Nullable SocketAddress remoteAddress() {
        return inetClient.remoteAddress();
    }

    boolean isClosed() {
        return inetClient.isClosed();
    }

    @CanIgnoreReturnValue
    TitanStompClientDriver onChannel(Handler<Channel> handler) {
        inetClient.onChannel(handler);
        return this;
    }

    @CanIgnoreReturnValue
    TitanStompClientDriver onStomp(Handler<StompClientHandler> handler) {
        this.stompClientHandler = handler;
        return this;
    }

    @Nullable StompClientChannel channel() {
        return connection;
    }

    private Promise<? extends NetChannel> connectTransport(InetSocketAddress remoteAddress, long timeoutMillis) {
        String webSocketPath = configuration.webSocketPath();
        if (webSocketPath == null) {
            return inetClient.connect(remoteAddress, timeoutMillis, TimeUnit.MILLISECONDS);
        }

        WebSocketClient webSocketClient = inetClient.upgradeWebSocket(Protocol.STOMP, webSocketPath);
        return webSocketClient.connect(remoteAddress, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private StompFrame createConnectFrame(String host) {
        StompHeaders headers = StompHeaders.create();
        headers.put(Elements.ACCEPT_VERSION, configuration.stompVersion().getVersion());
        if (!configuration.bypassHostHeader()) {
            headers.put(Elements.HOST, host);
        }
        if (configuration.virtualHost() != null) {
            headers.put(Elements.HOST, configuration.virtualHost());
        }
        if (configuration.login() != null) {
            headers.put(Elements.LOGIN, configuration.login());
        }
        if (configuration.passcode() != null) {
            headers.put(Elements.PASSCODE, configuration.passcode());
        }
        headers.put(
                Elements.HEART_BEAT,
                HeartBeat.create(configuration.heartbeatX(), configuration.heartbeatY()).value()
        );
        StompCommand command = configuration.useStompFrame() ? StompCommand.STOMP : StompCommand.CONNECT;
        return StompFrame.create(headers, command);
    }

    private Promise<StompClientChannel> awaitConnected(
            StompClientChannel connection,
            InetSocketAddress remoteAddress,
            long timeoutMillis
    ) {
        Promise<StompClientChannel> result = Promise.newPromise(connection.channel().eventLoop());
        ScheduledPromise<Object> timeout = connection.channel().eventLoop().schedule(() -> {
            if (result.tryFail(new StompNetChannelException(
                    "Timed out waiting for CONNECTED from " + remoteAddress
            ))) {
                connection.close();
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        connection.connectedPromise().addListener(connected -> {
            timeout.cancel();
            if (connected.isSuccess()) {
                result.trySuccess(connection);
                return;
            }

            Throwable error = connected.error();
            result.tryFail(error == null
                    ? new StompNetChannelException("STOMP connect failed without error cause")
                    : error);
        });
        return result;
    }
}
