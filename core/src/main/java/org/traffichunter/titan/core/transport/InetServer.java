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
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.channel.ChannelRegistry;

/**
 * TCP server transport with a single listening channel and many accepted child channels.
 *
 * <p>The inherited registry stores the server channel. Accepted client channels are kept in
 * {@code childChannels} so server lifecycle and connection fanout remain separate.</p>
 *
 * @author yungwang-o
 */
@Slf4j
public class InetServer extends AbstractTransport<NetServerChannel> {

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

    private void listen(InetSocketAddress address, ChannelPromise resultPromise) {
        bind(address).addListener(future -> {
            if (!future.isSuccess()) {
                state.compareAndSet(State.LISTENING, State.STARTED);
                resultPromise.fail(future.error());
                return;
            }

            ChannelPrimaryIOEventLoop eventLoop = groups().primaryGroup().next();
            eventLoop.register(() -> {
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

            return netChannel.eventLoop().submit(() -> netChannel.writeAndFlush(buffer));
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
    public boolean isStart() {
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

    private Promise<Void> bind(InetSocketAddress address) {
        return groups().primaryGroup().submit(() -> {
            try {
                channel.bind(address);
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

        @SuppressWarnings("unchecked")
        @Override
        public void accept(Channel channel) {
            if (!(channel instanceof NetChannel netChannel)) {
                throw new IllegalArgumentException("Unsupported channel: " + channel);
            }

            InetClientOption childOption = this.childOption;
            Handler<Channel> childHandler = this.childHandler;
            childOption.socketOptions().forEach((k, v) ->
                    netChannel.setOption((SocketOption<Object>) k, v)
            );

            // Accepted sockets must move to a secondary loop before reads are registered.
            ChannelSecondaryIOEventLoop loop = secondaryGroup.next();
            loop.register(() -> {
                try {
                    loop.ioSelector().registerRead(netChannel);
                    loop.register(netChannel);
                    channelRegistry.addChannel(netChannel);
                    childHandler.handle(netChannel);
                } catch (IOException e) {
                    throw new ServerException("Failed to init child channel", e);
                }
            });
        }
    }
}
