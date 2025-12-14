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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.channel.ChannelPrimaryIOEventLoop;
import org.traffichunter.titan.core.channel.ChannelEventLoopGroup;
import org.traffichunter.titan.core.channel.ChannelSecondaryIOEventLoop;
import org.traffichunter.titan.core.concurrent.EventLoopBridge;
import org.traffichunter.titan.core.concurrent.EventLoopBridges;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.channel.ChannelContext;
import org.traffichunter.titan.core.util.concurrent.Promise;

/**
 * @author yungwang-o
 */
@Slf4j
class InetServerImpl implements InetServer {

    private final ServerConnector connector;
    private final Object lock = new Object();

    private Handler<ChannelContext> channelContextHandler;

    private volatile ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> primaryGroup;
    private volatile ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup;

    private volatile boolean running = false;
    private volatile boolean listening = false;

    InetServerImpl(final ServerConnector connector) {
        this.connector = Objects.requireNonNull(connector, "connector");
    }

    @Override
    public InetServer start() {
        Assert.checkState(!running && connector.isOpen(), "Server is not listening");

        synchronized (lock) {
            running = true;
        }

        primaryGroup.start();
        secondaryGroup.start();

        ServerChannelDispatcher dispatcher = new ServerChannelDispatcher(primaryGroup, secondaryGroup, channelContextHandler);

        Thread channelDispathcerThread = new Thread(dispatcher, "ChannelDispatcherThread");
        channelDispathcerThread.start();

        return this;
    }

    @Override
    public InetServer group(ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> primaryGroup,
                            ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup) {
        Assert.checkNull(primaryGroup, "Primary group is null");
        Assert.checkNull(secondaryGroup, "Secondary group is null");

        this.primaryGroup = primaryGroup;
        this.secondaryGroup = secondaryGroup;
        return this;
    }

    @Override
    public Promise<Void> listen() {
        if(listening || !connector.isOpen()) {
            log.error("Connector is not open");

            return Promise.failedPromise(primaryGroup.next(), new ServerException("Connector is not open"));
        }

        synchronized (lock) {
            listening = true;
        }

        return bind();
    }

    @Override
    public InetServer invokeChannelHandler(final Handler<ChannelContext> channelContextHandler) {
        Assert.checkNull(channelContextHandler, "Channel context handler is null");
        this.channelContextHandler = channelContextHandler;
        return this;
    }

    @Override
    public InetServer exceptionHandler(final Handler<Throwable> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String host() {
        return connector.host();
    }

    @Override
    public int activePort() {
        throw new UnsupportedOperationException();
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
        return connector.isClosed();
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
                running = false;
                listening = false;
            }

            primaryGroup.gracefullyShutdown();
            secondaryGroup.gracefullyShutdown();
            connector.close();
            log.info("Closed server");
        } catch (IOException e) {
            throw new ServerException("Cannot close server", e);
        }
    }

    private Promise<Void> bind() {
        ChannelPrimaryIOEventLoop eventLoop = primaryGroup.next();
        return eventLoop.submit(() -> {
            try {
                connector.bind();
                eventLoop.ioSelector().registerAccept(connector.serverSocketChannel());
                return null;
            } catch (IOException e) {
                throw new ServerException("Failed to bind to " + connector.host(), e);
            }
        });
    }

    private static class ServerChannelDispatcher implements Runnable {

        private final ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> primaryGroup;
        private final ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup;
        private final Handler<ChannelContext> channelContextHandler;
        private final EventLoopBridge<ChannelContext> bridge = EventLoopBridges.getInstance();

        private ServerChannelDispatcher(ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> primaryGroup,
                                        ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> secondaryGroup,
                                        Handler<ChannelContext> channelContextHandler) {

            this.channelContextHandler = channelContextHandler;
            this.primaryGroup = primaryGroup;
            this.secondaryGroup = secondaryGroup;
        }

        @Override
        public void run() {

            while (primaryGroup.isStarted() && secondaryGroup.isStarted()) {
                ChannelContext ctx = bridge.consume();
                if(ctx == null) {
                    continue;
                }

                dispatch(ctx);
            }
        }

        /**
         * Dispatches the given context to a secondary event loop.
         *
         * <p>
         * Selector operations must run on the event loop’s own thread; enqueuing ensures that {@code handle(ctx)}
         * and {@code registerRead(ctx)} execute in the correct order and remain thread-safe.
         * </p>
         *
         * @param ctx the channel context to process and register for read events
         * @throws InetServer.ServerException if read registration fails
         */
        private void dispatch(ChannelContext ctx) {
            ChannelSecondaryIOEventLoop eventLoop = secondaryGroup.next();
            eventLoop.register(() -> {
                channelContextHandler.handle(ctx);
                try {
                    eventLoop.ioSelector().registerRead(ctx);
                } catch (IOException e) {
                    throw new ServerException("Failed to register I/O event for channel", e);
                }
            });
        }
    }
}