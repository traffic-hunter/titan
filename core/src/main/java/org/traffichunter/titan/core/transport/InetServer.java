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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.channel.ChannelRegistry;

/**
 * @author yungwang-o
 */
@Slf4j
public class InetServer extends AbstractTransport<NetServerChannel>{

    private final Object lock = new Object();

    private volatile boolean listening;

    private final ChannelRegistry<NetChannel> channelRegistry;

    private InetServer(NetServerChannel channel, EventLoopGroups groups, ChannelRegistry<NetChannel> channelRegistry) {
        super(channel, groups);
        this.channelRegistry = channelRegistry;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void start() {
        groups().primaryGroup().register(channel());
        groups().start();
    }

    public Promise<Void> listen(String host, int port) {
        return listen(new InetSocketAddress(host, port));
    }

    public Promise<Void> listen(InetSocketAddress address) {
        if (listening || !channel().isOpen()) {
            log.error("Connector is not open");
            return ChannelPromise.failedPromise(channel(), new ServerException("Connector is not open"));
        }

        ChannelPromise resultPromise = ChannelPromise.newPromise(channel());
        listen(address, resultPromise);
        return resultPromise;
    }

    private void listen(InetSocketAddress address, ChannelPromise resultPromise) {
        bind(address).addListener(future -> {
            if (!future.isSuccess()) {
                resultPromise.fail(future.error());
                return;
            }

            ChannelPrimaryIOEventLoop eventLoop = groups().primaryGroup().next();
            eventLoop.register(() -> {
                try {
                    eventLoop.ioSelector().registerAccept(channel());
                    synchronized (lock) {
                        listening = true;
                    }
                    resultPromise.success();
                } catch (IOException e) {
                    synchronized (lock) {
                        listening = false;
                    }
                    resultPromise.fail(new ServerException("Failed to register accept event", e));
                }
            });
        });
    }

    @Override
    public Promise<Void> send(Buffer buffer) {
        ChannelRegistry.ChannelSelector<NetChannel> selector = channelRegistry.selector();

        final Promise<Void> result;
        while (true) {
            if(selector.isEmpty()) {
                return ChannelPromise.failedPromise(channel(), new ServerException("Channel is empty"));
            }

            boolean hasNext = selector.hasNext();
            if (hasNext) {
                final NetChannel netChannel = selector.next();
                if (!netChannel.isActive() || netChannel.isClosed()) {
                    selector.remove();
                } else {
                    result = netChannel.eventLoop().submit(() -> netChannel.writeAndFlush(buffer));
                    break;
                }
            }
        }

        return result;
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown(long timeOut, TimeUnit timeUnit) {
        if (!listening) {
            log.warn("Server is not listening");
            return;
        }

        synchronized (lock) {
            listening = false;
        }

        log.info("Closing server...");
        try {
            super.close(timeOut, timeUnit);
            log.info("Closed server");
        } catch (Exception e) {
            throw new ServerException("Cannot close server", e);
        }
    }

    private Promise<Void> bind(InetSocketAddress address) {
        return groups().primaryGroup().submit(() -> {
            try {
                channel().bind(address);
            } catch (IOException e) {
                throw new ServerException("Failed to bind to " + channel().localAddress(), e);
            }
        });
    }

    public static final class Builder {

        private @Nullable EventLoopGroups groups;
        private @Nullable Handler<Channel> channelHandler;
        private Consumer<NetServerChannel> serverOptionApplier = channel -> { };
        private Consumer<NetChannel> childOptionApplier = channel -> { };
        private final ChannelRegistry<NetChannel> channelRegistry = new ChannelRegistry<>();

        @CanIgnoreReturnValue
        public Builder group(EventLoopGroups groups) {
            this.groups = groups;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder channelHandler(Handler<Channel> handler) {
            this.channelHandler = handler;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder option(Consumer<NetServerChannel> optionApplier) {
            this.serverOptionApplier = this.serverOptionApplier.andThen(optionApplier);
            return this;
        }

        @CanIgnoreReturnValue
        public Builder childOption(Consumer<NetChannel> optionApplier) {
            this.childOptionApplier = this.childOptionApplier.andThen(optionApplier);
            return this;
        }

        @CanIgnoreReturnValue
        @SuppressWarnings("unchecked")
        public Builder options(InetServerOption option) {
            serverOptionApplier = serverOptionApplier.andThen(channel ->
                    option.serverSocketOptions().forEach((k, v) ->
                            channel.setOption((SocketOption<Object>) k, v)
                    )
            );
            childOptionApplier = childOptionApplier.andThen(channel ->
                    option.childSocketOptions().forEach((k, v) ->
                            channel.setOption((SocketOption<Object>) k, v)
                    )
            );
            return this;
        }

        public InetServer build() {
            Assert.checkNotNull(groups, "eventLoopGroups");
            Assert.checkNotNull(channelHandler, "childHandler");

            NetServerChannel serverChannel;
            try {
                serverChannel = NetServerChannel.open(
                        new ServerChannelAcceptor(
                                channelHandler,
                                childOptionApplier,
                                groups.secondaryGroup(),
                                channelRegistry
                        )
                );
            } catch (IOException e) {
                throw new ServerException("Failed to open server channel", e);
            }

            serverOptionApplier.accept(serverChannel);

            groups.register(serverChannel);
            return new InetServer(serverChannel, groups, channelRegistry);
        }
    }

    private static final class ServerChannelAcceptor implements ChannelHandShakeEventListener {

        private final Handler<Channel> childHandler;
        private final Consumer<NetChannel> childOptionApplier;
        private final ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup;
        private final ChannelRegistry<NetChannel> channelRegistry;

        ServerChannelAcceptor(
                Handler<Channel> childHandler,
                Consumer<NetChannel> childOptionApplier,
                ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup,
                ChannelRegistry<NetChannel> channelRegistry
        ) {
            this.childHandler = childHandler;
            this.childOptionApplier = childOptionApplier;
            this.secondaryGroup = secondaryGroup;
            this.channelRegistry = channelRegistry;
        }

        @Override
        public void accept(Channel channel) {
            if (!(channel instanceof NetChannel netChannel)) {
                throw new IllegalArgumentException("Unsupported channel: " + channel);
            }

            childOptionApplier.accept(netChannel);

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
