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
package org.traffichunter.titan.core.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.net.TlsHandler;
import org.traffichunter.titan.core.net.TlsSide;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.transport.websocket.WebSocketServerHandshaker;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.channel.ChannelRegistry;

/**
 * TCP server transport with a single listening channel and many accepted child channels.
 *
 * <p>The inherited registry stores the server channel. Accepted client channels are kept in
 * {@code childChannels} so server lifecycle and connection fanout remain separate.</p>
 *
 * @author yungwang-o
 */
public class InetServer extends AbstractTransport<NetServerChannel> {

    private static final Logger log = LoggerFactory.getLogger(InetServer.class);

    private final NetServerChannel channel;
    private final ChannelRegistry<NetChannel> childChannels;
    private final ServerChannelAcceptor acceptor;
    private final AtomicReference<State> state = new AtomicReference<>(State.INIT);

    private InetServerOption option = InetServerOption.DEFAULT_INET_SERVER_OPTION;

    private enum State {
        INIT,
        STARTED,
        LISTENING,
        SHUTDOWN
    }

    private InetServer(
            EventLoopGroups groups,
            ChannelRegistry<NetChannel> childChannels,
            ServerChannelAcceptor acceptor
    ) {
        super(NewIONetServerChannel.class, groups);
        this.channel = newChannel(acceptor);
        this.childChannels = childChannels;
        this.acceptor = acceptor;
    }

    public static InetServer open(EventLoopGroups groups) {
        ChannelRegistry<NetChannel> channelRegistry = new ChannelRegistry<>();
        ServerChannelAcceptor acceptor = new ServerChannelAcceptor(
                groups.secondaryGroup(),
                channelRegistry
        );

        InetServer server = new InetServer(groups, channelRegistry, acceptor);
        groups.register(server.channel);
        return server;
    }

    @CanIgnoreReturnValue
    public InetServer option(InetServerOption option) {
        this.option = option;
        return this;
    }

    @CanIgnoreReturnValue
    public InetServer childOption(InetClientOption childOption) {
        this.acceptor.setChildOption(childOption);
        return this;
    }

    @CanIgnoreReturnValue
    public InetServer tls(TlsContext tlsContext) {
        if (isStarted()) {
            throw new IllegalStateException("Cannot configure TLS after server start");
        }
        if (tlsContext.side() != TlsSide.SERVER) {
            throw new IllegalStateException("InetServer requires a server-side TLS context");
        }

        this.acceptor.setTlsContext(tlsContext);
        return this;
    }

    /**
     * Enables WebSocket upgrade handling at the default root path.
     *
     * <p>This setting must be applied before {@link #start()}.</p>
     */
    @CanIgnoreReturnValue
    public InetServer upgradeWebSocket() {
        if (isStarted()) {
            throw new IllegalStateException("Cannot configure WebSocket upgrade after server start");
        }

        return upgradeWebSocket("/");
    }

    /**
     * Enables HTTP Upgrade handling for newly accepted connections at the supplied path.
     *
     * <p>The server still listens on its normal TCP socket. Each accepted connection must finish
     * the WebSocket handshake before the configured child channel handler is invoked.</p>
     *
     * @param path HTTP request path accepted by the WebSocket handshaker; a leading slash is optional
     * @return this server
     */
    @CanIgnoreReturnValue
    public InetServer upgradeWebSocket(String path) {
        if (isStarted()) {
            throw new IllegalStateException("Cannot configure WebSocket upgrade after server start");
        }

        this.acceptor.enableWebSocketUpgrade(path);
        return this;
    }

    /**
     * Registers a callback invoked for accepted child channels.
     *
     * <p>The callback may run on an event-loop thread. Do not run blocking code here.</p>
     */
    @CanIgnoreReturnValue
    public InetServer onChannel(Handler<Channel> handler) {
        this.acceptor.setChildHandler(handler);
        return this;
    }

    @Override
    public void start() {
        if (!state.compareAndSet(State.INIT, State.STARTED)) {
            throw new IllegalStateException("Cannot start server from state=" + state.get());
        }

        try {
            applyServerOptions(option);
            groups().primaryGroup().register(channel);
            groups().start();
            log.info(
                    "Started InetServer. session={}, serverOptions={}, childOptions={}",
                    channel.session(),
                    option.serverSocketOptions(),
                    option.childSocketOptions()
            );
        } catch (RuntimeException e) {
            state.compareAndSet(State.STARTED, State.INIT);
            throw e;
        }
    }

    public Promise<Void> listen(String host, int port) {
        return listen(new InetSocketAddress(host, port));
    }

    public Promise<Void> listen(InetSocketAddress address) {
        State current = state.get();
        if (current != State.STARTED || !channel.isOpen()) {
            return ChannelPromise.failedPromise(channel, new ServerException("Server is not ready to listen. state=" + current));
        }
        if (!state.compareAndSet(State.STARTED, State.LISTENING)) {
            return ChannelPromise.failedPromise(channel, new ServerException("Failed to transition to LISTENING. state=" + state.get()));
        }

        ChannelPromise resultPromise = ChannelPromise.newPromise(channel);
        listen(address, resultPromise);
        return resultPromise;
    }

    @Override
    public Promise<Void> send(Buffer buffer) {
        ChannelRegistry.ChannelSelector<NetChannel> selector = childChannels.selector();

        while (true) {
            if (childChannels.isEmpty()) {
                return ChannelPromise.failedPromise(channel, new ServerException("Channel is empty"));
            }

            NetChannel netChannel = selector.next();
            if (!netChannel.isActive() || netChannel.isClosed()) {
                childChannels.removeChannel(netChannel);
                continue;
            }

            return netChannel.writeAndFlush(buffer);
        }
    }

    public List<NetChannel> childChannel() {
        return childChannels.getChannels();
    }

    @Override
    public NetServerChannel channel() {
        return channel;
    }

    @Override
    public boolean isStarted() {
        State current = state.get();
        return current == State.STARTED || current == State.LISTENING;
    }

    @Override
    public boolean isShutdown() {
        return state.get() == State.SHUTDOWN && groups().isShuttingDown();
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown(long timeOut, TimeUnit timeUnit) {
        State current = state.get();
        if (current == State.SHUTDOWN) {
            log.warn("Server is already shutdown");
            return;
        }
        if (current == State.INIT) {
            log.warn("Server is not started");
            return;
        }
        if (!state.compareAndSet(current, State.SHUTDOWN)) {
            log.warn("Server is already shutdown");
            return;
        }

        log.info("Closing server...");

        try {
            close(timeOut, timeUnit);
            log.info("Closed server");
        } catch (Exception e) {
            throw new ServerException("Cannot close server", e);
        }
    }

    private void listen(InetSocketAddress address, ChannelPromise resultPromise) {
        bind(address).addListener(future -> {
            if (!future.isSuccess()) {
                state.compareAndSet(State.LISTENING, State.STARTED);
                Throwable error = future.error();
                resultPromise.fail(error != null ? error : new ServerException("Server bind failed without error"));
                return;
            }

            ChannelPrimaryIOEventLoop eventLoop = groups().primaryGroup().next();
            eventLoop.execute(() -> {
                try {
                    eventLoop.ioSelector().registerAccept(channel);
                    log.info("InetServer listen ready. session={}, address={}", channel.session(), address);
                    resultPromise.success();
                } catch (IOException e) {
                    state.compareAndSet(State.LISTENING, State.STARTED);
                    resultPromise.fail(new ServerException("Failed to register accept event", e));
                }
            });
        });
    }

    private Promise<Void> bind(InetSocketAddress address) {
        return groups().primaryGroup().submit(() -> {
            try {
                channel.internal().bind(address);
            } catch (IOException e) {
                throw new ServerException("Failed to bind to " + channel.localAddress(), e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void applyServerOptions(InetServerOption option) {
        option.serverSocketOptions().forEach((k, v) ->
                channel.setOption((SocketOption<Object>) k, v)
        );
    }

    private static final class ServerChannelAcceptor implements ChannelHandShakeEventListener {

        private final ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup;
        private final ChannelRegistry<NetChannel> channelRegistry;

        private volatile InetClientOption childOption = InetClientOption.DEFAULT_INET_CLIENT_OPTION;
        private volatile Handler<Channel> childHandler = ch -> {};
        private volatile boolean webSocketUpgrade;
        private volatile WebSocketServerHandshaker webSocketHandshaker = new WebSocketServerHandshaker();
        private volatile @Nullable TlsContext tlsContext;

        ServerChannelAcceptor(
                ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup,
                ChannelRegistry<NetChannel> channelRegistry
        ) {
            this.secondaryGroup = secondaryGroup;
            this.channelRegistry = channelRegistry;
        }

        void setChildOption(InetClientOption childOption) {
            this.childOption = childOption;
        }

        void setChildHandler(Handler<Channel> childHandler) {
            this.childHandler = childHandler;
        }

        void enableWebSocketUpgrade(String path) {
            this.webSocketHandshaker = new WebSocketServerHandshaker(path);
            this.webSocketUpgrade = true;
        }

        void setTlsContext(TlsContext tlsContext) {
            this.tlsContext = tlsContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void accept(Channel channel) {
            if (!(channel instanceof NetChannel netChannel)) {
                throw new IllegalArgumentException("Unsupported channel: " + channel);
            }

            childOption.socketOptions().forEach((k, v) ->
                    netChannel.setOption((SocketOption<Object>) k, v)
            );

            // Accepted sockets must move to a secondary loop before reads are registered.
            ChannelSecondaryIOEventLoop loop = secondaryGroup.next();
            loop.execute(() -> {
                try {
                    loop.ioSelector().registerRead(netChannel);
                    loop.register(netChannel);
                    channelRegistry.addChannel(netChannel);

                    TlsContext tlsCtx = this.tlsContext;
                    if (tlsCtx == null) {
                        runTasks(netChannel);
                        return;
                    }

                    InetSocketAddress addr = (InetSocketAddress) netChannel.remoteAddress();
                    if (addr == null) {
                        throw new ServerException("No remote address set");
                    }

                    TlsHandler tlsHandler = tlsCtx.newHandler(addr.getHostString(), addr.getPort());
                    netChannel.chain().addFirst(tlsHandler);

                    tlsHandler.handshake(netChannel)
                            .onSuccess(ignored -> {
                                log.info("Successfully TLS connected to {}:{}", addr.getHostString(), addr.getPort());
                                runTasks(netChannel);
                            }).onFailure(error -> {
                                log.error("Failed to handshake", error);
                                closeChild(netChannel, error);
                            });

                } catch (Exception e) {
                    throw new ServerException("Failed to init child channel", e);
                }
            });
        }

        private void runTasks(NetChannel channel) {
            if (webSocketUpgrade) {
                webSocketHandshaker.handshake(channel).addListener(result -> {
                    if (result.isSuccess()) {
                        childHandler.handle(channel);
                    } else {
                        closeChild(channel, result.error());
                    }
                });
                return;
            }

            childHandler.handle(channel);
        }

        private void closeChild(
                NetChannel channel,
                @Nullable Throwable error
        ) {
            channelRegistry.removeChannel(channel);

            if (error != null) {
                log.error(
                        "Failed to initialize child channel. channel={}",
                        channel.id(),
                        error
                );
            }

            channel.close();
        }
    }
}
