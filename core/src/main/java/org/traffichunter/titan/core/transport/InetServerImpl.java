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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.event.EventLoops;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.inet.ReadHandler;

/**
 * @author yungwang-o
 */
@Slf4j
class InetServerImpl implements InetServer {

    private final ServerConnector connector;

    private final EventLoops eventLoops;

    private final GlobalShutdownHook shutdownHook = GlobalShutdownHook.INSTANCE;

    private final Object lock = new Object();

    private Handler<SocketChannel> connectHandler;

    private volatile boolean listening = false;

    InetServerImpl(final ServerConnector connector) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.eventLoops = new EventLoops();
    }

    @Override
    public void start() {
        if(!listening || !connector.isOpen()) {
            throw new IllegalStateException("Server is not listening");
        }

        log.info("launched server!!");
        eventLoops.start();
    }

    @Override
    public CompletableFuture<InetServer> listen() {
        if(!connector.isOpen()) {
            log.error("Connector is not open");
            return CompletableFuture.failedFuture(new IllegalStateException("Connector is not open"));
        }

        return bind().thenApply(server -> {
                listening = true;
                return server;
            }).whenComplete(((server, ex) -> {
                if(ex != null) {
                    log.error("Failed to listen on host = {}. port = {}, exception = {}",
                            connector.host(),
                            connector.host(),
                            ex.getMessage()
                    );
                }
            }));
    }

    @Override
    public InetServer onConnect(final Handler<SocketChannel> handler) {
        Objects.requireNonNull(handler, "connectHandler");
        this.connectHandler = handler;
        return this;
    }

    @Override
    public InetServer onRead(final ReadHandler readHandler) {
        Objects.requireNonNull(readHandler, "readHandler");

        eventLoops.registerHandler(new ChannelContextInBoundHandler() {

           @Override
           public void handleRead(final ChannelContext channelContext) {
               Buffer buffer = Buffer.alloc(1024);

               int recv = channelContext.recv(buffer);
               if(recv > 0) {
                   try {
                       readHandler.handle(buffer);
                   } finally {
                       buffer.release();
                   }
               } else if(recv < 0) {
                   try {
                       channelContext.close();
                   } catch (IOException e) {
                       log.info("Failed to close socket = {}", e.getMessage());
                       doClose();
                   } finally {
                       buffer.release();
                   }
               }
           }

        });

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
    public void shutdown(final boolean isGraceful) {
        if(isGraceful) {
            if(shutdownHook.isEnabled()) {
                shutdownHook.addShutdownCallback(this::doClose);
            }
            return;
        }

        doClose();
    }

    private CompletableFuture<InetServer> bind() {

        try {
            connector.bind();
            eventLoops.primary().registerIoConcern(connector.serverSocketChannel());
        } catch (IOException e) {
            log.error("Failed to connect to {}.", connector.host(), e);
            close();
        }

        return CompletableFuture.completedFuture(this);
    }

    @SuppressWarnings("SameParameterValue")
    @Deprecated
    private void accept(final Selector selector) {
        if(!connector.isOpen()) {
            throw new ServerException("Connector is not open");
        }

        try {
            log.info("Accepting inet server connection");
            SocketChannel client = connector.serverSocketChannel().accept();

            connectHandler.handle(client);

            if(client == null) {
                log.error("Failed to accept inet server connection");
                return;
            }

            client.configureBlocking(false);
            ChannelContext channelContext = ChannelContext.create(client);
            client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, channelContext);
        } catch (IOException e) {
            throw new ServerException("Failed accept", e);
        }
    }

    @ThreadSafe
    private void doClose() {
        if(!listening) {
            log.warn("Not listening");
            return;
        }

        log.info("Closing server...");
        try {
            synchronized (lock) {
                listening = false;
            }

            eventLoops.shutdown();
            connector.close();
            log.info("Closed server");
        } catch (IOException e) {
            throw new ServerException("Cannot close server", e);
        }
    }

    private void configureKeepAlive(final boolean onOff) {

    }
}