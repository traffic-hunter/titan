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
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.concurrent.Promise;

/**
 * @author yungwang-o
 */
@Slf4j
class InetServerImpl implements InetServer {

    private final NetServerChannel channel;
    private final Object lock = new Object();

    private volatile ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> primaryGroup;
    private volatile ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup;

    private volatile Handler<Channel> channelHandler;

    private final Map<SocketOption<?>, Object> childOptions = new HashMap<>();

    private volatile boolean listening = false;

    InetServerImpl() {
        try {
            this.channel = NetServerChannel.open();
        } catch (IOException e) {
            throw new ServerException("Failed to open server channel", e);
        }
    }

    @Override
    public InetServer start() {
        Assert.checkState(!listening && channel.isOpen(), "Server is not listening");

        primaryGroup.start();
        secondaryGroup.start();

        primaryGroup.next().register(() ->
                channel.init(new ServerChannelDispatcher(channelHandler, secondaryGroup))
        );

        return this;
    }

    @NonNull
    @Override
    public InetServer group(ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> primaryGroup,
                            ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup) {
        this.primaryGroup = primaryGroup;
        this.secondaryGroup = secondaryGroup;
        return this;
    }

    @Override
    public <T> InetServer option(SocketOption<T> option, T value) {
        channel.setOption(option, value);
        return this;
    }

    @Override
    public <T> InetServer childOption(SocketOption<T> option, T value) {
        synchronized (lock) {
            childOptions.put(option, value);
        }
        return this;
    }

    @Override
    public InetServer channelHandler(Handler<Channel> channelHandler) {
        this.channelHandler = channelHandler;
        return this;
    }

    @Override
    public Promise<Void> listen(InetSocketAddress address) {
        if(listening || !channel.isOpen()) {
            log.error("Connector is not open");

            return Promise.failedPromise(primaryGroup.next(), new ServerException("Connector is not open"));
        }

        synchronized (lock) {
            listening = true;
        }

        return bind(address).addListener(future -> {
            if(future.isSuccess()) {
                ChannelPrimaryIOEventLoop eventLoop = primaryGroup.next();
                eventLoop.register(() -> {
                    try {
                        eventLoop.ioSelector().registerAccept(channel);
                    } catch (IOException e) {
                        throw new ServerException("Failed to register accept event", e);
                    }
                });
            }
        });
    }

    @Override
    public InetServer exceptionHandler(Handler<Throwable> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable SocketAddress localAddress() {
        return channel.localAddress();
    }

    @Override
    public boolean isStart() {
        return this.listening;
    }

    @Override
    public boolean isListening() {
        return this.listening;
    }

    @Override
    public boolean isClosed() {
        return channel.isClosed();
    }

    @Override
    public void shutdown() {
        if(!listening) {
            log.warn("Not listening");
            return;
        }

        log.info("Closing server...");
        try {
            synchronized (lock) {
                listening = false;
            }

            primaryGroup.gracefullyShutdown();
            secondaryGroup.gracefullyShutdown();
            channel.close();
            log.info("Closed server");
        } catch (Exception e) {
            throw new ServerException("Cannot close server", e);
        }
    }

    private Promise<Void> bind(InetSocketAddress address) {
        ChannelPrimaryIOEventLoop eventLoop = primaryGroup.next();
        return eventLoop.submit(() -> {
            try {
                channel.bind(address);
                return null;
            } catch (IOException e) {
                throw new ServerException("Failed to bind to " + channel.localAddress(), e);
            }
        });
    }

    private static class ServerChannelDispatcher implements ChannelInitializer {

        private final Handler<Channel> channelHandler;
        private final ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup;

        public ServerChannelDispatcher(Handler<Channel> channelHandler,
                                       ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup) {
            this.channelHandler = channelHandler;
            this.secondaryGroup = secondaryGroup;
        }

        @Override
        public void initChannel(Channel channel) {
            if(channel instanceof NetChannel netChannel) {
                ChannelSecondaryIOEventLoop eventLoop = secondaryGroup.next();
                eventLoop.register(() -> {
                    try {
                        eventLoop.ioSelector().registerRead(netChannel);
                        channelHandler.handle(channel);
                    } catch (IOException e) {
                        throw new ServerException("Failed to register read event", e);
                    }
                });
            } else {
                throw new IllegalArgumentException("Unsupported channel type: " + channel.getClass());
            }
        }
    }
}