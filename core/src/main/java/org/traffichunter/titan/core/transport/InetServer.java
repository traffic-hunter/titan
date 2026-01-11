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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.concurrent.Promise;

/**
 * @author yungwang-o
 */
@Slf4j
public class InetServer extends AbstractTransport<NetServerChannel>{

    private final Object lock = new Object();

    private volatile boolean listening;

    private InetServer(NetServerChannel channel, EventLoopGroups groups) {
        super(channel, groups);
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
        if(listening || !channel().isOpen()) {
            log.error("Connector is not open");

            return Promise.failedPromise(groups().primaryGroup().next(), new ServerException("Connector is not open"));
        }

        synchronized (lock) {
            listening = true;
        }

        return bind(address).addListener(future -> {
            if(future.isSuccess()) {
                ChannelPrimaryIOEventLoop eventLoop = groups().primaryGroup().next();
                eventLoop.register(() -> {
                    try {
                        eventLoop.ioSelector().registerAccept(channel());
                    } catch (IOException e) {
                        throw new ServerException("Failed to register accept event", e);
                    }
                });
            }
        });
    }

    public void shutdown() {
        shutdown(30, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown(long timeOut, TimeUnit timeUnit) {
        synchronized (lock) {
            if (!listening) {
                log.warn("Server is not listening");
                return;
            }
            listening = false;
        }

        log.info("Closing server...");
        try {
            synchronized (lock) {
                listening = false;
            }

            super.close(timeOut, timeUnit);
            log.info("Closed server");
        } catch (Exception e) {
            throw new ServerException("Cannot close server", e);
        }
    }

    private Promise<Void> bind(InetSocketAddress address) {
        ChannelPrimaryIOEventLoop eventLoop = groups().primaryGroup().next();
        return eventLoop.submit(() -> {
            try {
                channel().bind(address);
                return null;
            } catch (IOException e) {
                throw new ServerException("Failed to bind to " + channel().localAddress(), e);
            }
        });
    }

    public static final class Builder {

        private EventLoopGroups groups;
        private Handler<Channel> channelHandler;
        private final Map<SocketOption<?>, Object> options = new HashMap<>();
        private final Map<SocketOption<?>, Object> childOptions = new HashMap<>();

        public Builder group(EventLoopGroups groups) {
            this.groups = groups;
            return this;
        }

        public Builder channelHandler(Handler<Channel> handler) {
            this.channelHandler = handler;
            return this;
        }

        public <T> Builder option(SocketOption<T> option, T value) {
            options.put(option, value);
            return this;
        }

        public <T> Builder childOption(SocketOption<T> option, T value) {
            childOptions.put(option, value);
            return this;
        }

        @SuppressWarnings("unchecked")
        public InetServer build() {
            Assert.checkNull(groups, "eventLoopGroups");
            Assert.checkNull(channelHandler, "childHandler");

            NetServerChannel serverChannel;
            try {
                serverChannel = NetServerChannel.open(
                        new ServerChannelAcceptor(
                                channelHandler,
                                childOptions,
                                groups.secondaryGroup()
                        )
                );
            } catch (IOException e) {
                throw new ServerException("Failed to open server channel", e);
            }

            options.forEach((k, v) ->
                    serverChannel.setOption((SocketOption<Object>) k, v)
            );

            groups.register(serverChannel);
            return new InetServer(serverChannel, groups);
        }
    }

    @SuppressWarnings("unchecked")
    private static final class ServerChannelAcceptor implements ChannelHandShakeEventListener {

        private final Handler<Channel> childHandler;
        private final Map<SocketOption<?>, Object> childOptions;
        private final ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup;

        ServerChannelAcceptor(
                Handler<Channel> childHandler,
                Map<SocketOption<?>, Object> childOptions,
                ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup
        ) {
            this.childHandler = childHandler;
            this.childOptions = childOptions;
            this.secondaryGroup = secondaryGroup;
        }

        @Override
        public void accept(Channel channel) {
            if (!(channel instanceof NetChannel netChannel)) {
                throw new IllegalArgumentException("Unsupported channel: " + channel);
            }

            childOptions.forEach((k, v) ->
                    netChannel.setOption((SocketOption<Object>) k, v)
            );

            ChannelSecondaryIOEventLoop loop = secondaryGroup.next();
            loop.register(() -> {
                try {
                    loop.ioSelector().registerRead(netChannel);
                    loop.register(netChannel);
                    childHandler.handle(netChannel);
                } catch (IOException e) {
                    throw new ServerException("Failed to init child channel", e);
                }
            });
        }
    }
}