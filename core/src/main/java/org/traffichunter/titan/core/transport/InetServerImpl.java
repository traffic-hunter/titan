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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.event.EventLoop;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.inet.InetConstants;
import org.traffichunter.titan.core.util.inet.ReadHandler;
import org.traffichunter.titan.core.util.inet.WriteHandler;

/**
 * @author yungwang-o
 */
@Slf4j
class InetServerImpl implements InetServer {

    private final ServerConnector connector;

    private final GlobalShutdownHook shutdownHook = GlobalShutdownHook.INSTANCE;

    private final EventLoop inetServerEventLoop;

    private final Selector selector;

    private final Object lock = new Object();

    private final Thread listenerThread = new Thread(this::start, InetConstants.INET_SERVER_LISTENER_THREAD);

    private ReadHandler<byte[]> readHandler;

    private WriteHandler<byte[]> writeHandler;

    private volatile boolean listening = false;

    InetServerImpl(final ServerConnector connector, final EventLoop inetServerEventLoop) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.inetServerEventLoop = Objects.requireNonNull(inetServerEventLoop, "inetServerEventLoop");

        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public CompletableFuture<InetServer> listen() {
        return listen(new InetSocketAddress(connector.host(), connector.port()));
    }

    @Override
    public synchronized CompletableFuture<InetServer> listen(final InetSocketAddress address) {
        if(!connector.isOpen()) {
            log.error("Connector is not open");
            return CompletableFuture.failedFuture(new IllegalStateException("Connector is not open"));
        }

        listening = true;

        log.info("Listening on host = {}. port = {}", address.getHostString(), address.getPort());

        return bind(address).exceptionally(ex -> {
            log.error("Failed to listen on host = {}. port = {}, exception = {}", address.getHostString(), address.getPort(), ex.getMessage());
            close();
            listening = false;
            return this;
        });
    }

    @Override
    public InetServer onRead(final ReadHandler<byte[]> handler) {
        Objects.requireNonNull(handler, "handler");
        readHandler = handler;
        return this;
    }

    @Override
    public InetServer onWrite(final WriteHandler<byte[]> handler) {
        Objects.requireNonNull(handler, "handler");
        writeHandler = handler;
        return null;
    }

    @Override
    public InetServer exceptionHandler(final Consumer<Throwable> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int activePort() {
        return connector.port();
    }

    @Override
    public boolean isListening() {
        return connector.isOpen() && listening;
    }

    @Override
    public boolean isClosed() {
        return connector.isClosed();
    }

    @Override
    public CompletableFuture<Void> shutdown(final boolean isGraceful) {
        if(isGraceful) {
            if(shutdownHook.isEnabled()) {
                return CompletableFuture.runAsync(() ->
                        shutdownHook.addShutdownCallback(this::doClose));
            }
        }

        return CompletableFuture.runAsync(this::doClose);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(this::doClose);
    }

    private CompletableFuture<InetServer> bind(final InetSocketAddress address) {

        listenerThread.start();

        return inetServerEventLoop.submit(() -> {
            try {
                connector.bind(address);

                connector.register(selector);

                return this;
            } catch (IOException e) {
                throw new ServerException("Failed bind", e);
            }
        });
    }

    @SuppressWarnings("SameParameterValue")
    private void accept(final boolean isBlocking, final SelectionKey key) {
        try {
            SocketChannel socketChannel = connector.accept(isBlocking);
            ChannelContext channelContext = ChannelContext.create(socketChannel);
            key.attach(channelContext);
        } catch (IOException e) {
            throw new ServerException("Failed accept", e);
        }
    }

    @ThreadSafe
    private void doClose() {
        if(!listening) {
            log.error("Already listening");
            return;
        }

        try {
            synchronized (lock) {
                listening = false;
            }
            inetServerEventLoop.gracefulShutdown(10, TimeUnit.SECONDS);
            selector.close();
            connector.close();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot close server", e);
        }
    }

    private void start() {
        log.info("Launch server on {}:{}", connector.host(), connector.port());

        while (isListening() && selector.isOpen()) {
            try {
                int select = selector.select();
                if (select == 0) {
                    continue;
                }
            } catch (IOException e) {
                throw new ServerException(e);
            }

            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();

                if(key.isAcceptable()) {
                    accept(false, key);
                } else {
                    if(key.isReadable()) {
                        try (ChannelContext cc = ChannelContext.select(key)) {
                            ByteBuffer buf = ByteBuffer.allocate(8 * 1024);
                            inetServerEventLoop.submit(() -> cc.recv(buf))
                                    .thenCompose(read -> {
                                        if(read <= 0) {
                                            throw new ServerException("Failed to read channel");
                                        }

                                        // TODO Considering gc optimization
                                        buf.flip();
                                        byte[] data = new byte[buf.remaining()];
                                        buf.get(data);
                                        readHandler.handle(data);
                                        return null;
                                    }).exceptionally(throwable -> {
                                        log.error("Recv error = {}", throwable.getMessage());
                                        doClose();
                                        return null;
                                    });
                        } catch (IOException e) {
                            throw new ServerException("Failed readable", e);
                        }
                    }

                    if(key.isWritable()) {
                        try (ChannelContext cc = ChannelContext.select(key)){
                            ByteBuffer buf  = ByteBuffer.allocate(8 * 1024);
                            byte[] data = writeHandler.handle();
                            buf.compact();
                            buf.put(data);
                            inetServerEventLoop.submit(() -> cc.send(buf))
                                    .thenCompose(write -> {
                                        if(write <= 0) {
                                            throw new ServerException("Failed to write channel");
                                        }
                                        return null;
                                    });
                        } catch (IOException e) {
                            throw new ServerException(e);
                        }
                    }
                }

                iter.remove();
            }
        }
    }
}