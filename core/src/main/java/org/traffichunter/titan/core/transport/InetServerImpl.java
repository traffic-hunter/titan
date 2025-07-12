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
import java.net.InetAddress;
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

    private final Thread listenerThread = new Thread(this::start0, InetConstants.INET_SERVER_LISTENER_THREAD);

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
    public void start() {
        if(!listening) {
            throw new IllegalStateException("Server is not listening");
        }

        listenerThread.start();
    }

    @Override
    public CompletableFuture<InetServer> listen() {
        return listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), InetConstants.DEFAULT_PORT));
    }

    @Override
    public synchronized CompletableFuture<InetServer> listen(final InetSocketAddress address) {
        if(!connector.isOpen()) {
            log.error("Connector is not open");
            return CompletableFuture.failedFuture(new IllegalStateException("Connector is not open"));
        }

        if(address.isUnresolved()) {
            log.error("Address is unresolved");
            return CompletableFuture.failedFuture(new IllegalStateException("Address is unresolved"));
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
        return this;
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
    public void shutdown(final boolean isGraceful) {
        if(isGraceful) {
            if(shutdownHook.isEnabled()) {
                shutdownHook.addShutdownCallback(this::doClose);
            }
            return;
        }

        doClose();
    }

    @Override
    public void close() {
        doClose();
    }

    private CompletableFuture<InetServer> bind(final InetSocketAddress address) {

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
    private void accept(final boolean isBlocking) {
        try {
            SocketChannel client = connector.accept(isBlocking);
            ChannelContext channelContext = ChannelContext.create(client);
            client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, channelContext);
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

        log.info("Closing server...");
        try {
            synchronized (lock) {
                listening = false;
            }
            inetServerEventLoop.gracefulShutdown(10, TimeUnit.SECONDS);
            selector.close();
            connector.close();
            log.info("Closed server");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot close server", e);
        }
    }

    private void start0() {

        log.info("Server launched!!");

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
                    accept(false);
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
                                        buf.clear();
                                        return CompletableFuture.completedFuture(data);
                                    }).exceptionally(throwable -> {
                                        log.error("Recv error = {}", throwable.getMessage());
                                        return null;
                                    });
                        } catch (IOException e) {
                            throw new ServerException("Failed readable", e);
                        }
                    }

                    if(key.isWritable()) {
                        try (ChannelContext cc = ChannelContext.select(key)){
                            byte[] data = writeHandler.handle();
                            ByteBuffer buf = ByteBuffer.wrap(data);
                            inetServerEventLoop.submit(() -> cc.send(buf))
                                    .thenCompose(write -> {
                                        if(write <= 0) {
                                            throw new ServerException("Failed to write channel");
                                        }
                                        return CompletableFuture.completedFuture(data);
                                    });
                        } catch (IOException e) {
                            throw new ServerException("Failed writable" ,e);
                        }
                    }
                }

                iter.remove();
            }
        }
    }
}