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
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.GlobalShutdownHook;
import org.traffichunter.titan.core.event.SecondaryNioEventLoop;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.channel.ChannelContext;
import org.traffichunter.titan.core.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelContextOutBoundHandler;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;
import org.traffichunter.titan.core.util.event.IOType;

/**
 * @author yungwang-o
 */
@Slf4j
class InetClientImpl implements InetClient {

    private final ClientConnector connector;
    private final SecondaryNioEventLoop eventLoop;
    private final GlobalShutdownHook shutdownHook = GlobalShutdownHook.INSTANCE;
    private final Queue<Buffer> writePendingTask = new ConcurrentLinkedQueue<>();
    private final ChannelContext ctx;

    private Handler<SocketChannel> connectHandler;
    private Handler<Buffer> readHandler;
    private Handler<Buffer> writeHandler;
    private Handler<Throwable> exceptionHandler;

    public InetClientImpl(final InetSocketAddress address) {
        Assert.checkNull(address, "Address is null");

        this.connector = ClientConnector.open(address);
        this.eventLoop = new SecondaryNioEventLoop();
        this.ctx = ChannelContext.create(connector.channel());
    }

    @Override
    public InetClient start() {
        eventLoop.registerChannelContextHandler(new ChannelContextInBoundHandler() {
            @Override
            public void handleConnect(final ChannelContext channelContext) {
                connectHandler.handle(channelContext.socketChannel());
            }

            @Override
            public void handleCompletedConnect(final ChannelContext channelContext) {
                eventLoop.registerIoChannel(channelContext, IOType.READ);
            }

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
                        doClose(false);
                    } finally {
                        buffer.release();
                    }
                }
            }
        });

        eventLoop.registerChannelContextHandler(new ChannelContextOutBoundHandler() {
            @Override
            public void handleWrite(final ChannelContext channelContext) {
                Buffer buffer;
                while ((buffer = writePendingTask.poll()) != null) {
                    writeHandler.handle(buffer);
                    int write = channelContext.write(buffer);
                    if(write == buffer.byteBuf().readableBytes()) {
                        buffer.release();
                    } else {
                        eventLoop.registerIoChannel(channelContext, IOType.WRITE);
                        break;
                    }
                }
            }
        });

        eventLoop.registerIoChannel(ctx, IOType.CONNECT);
        eventLoop.start();
        return this;
    }

    @Override
    public InetClient setOption(ClientOptions options) {
        if(options == null) {
            options = ClientOptions.DEFAULT;
        }

        try {
            connector.channel()
                    .setOption(StandardSocketOptions.TCP_NODELAY, options.tcpNoDelay())
                    .setOption(StandardSocketOptions.SO_KEEPALIVE, options.keepAlive())
                    .setOption(StandardSocketOptions.SO_SNDBUF, options.sendBufferSize())
                    .setOption(StandardSocketOptions.SO_RCVBUF, options.receiveBufferSize())
                    .setOption(StandardSocketOptions.SO_REUSEADDR, options.reuseAddr())
                    .setOption(StandardSocketOptions.SO_REUSEPORT, options.reusePort());
        } catch (IOException ignored) { }

        return this;
    }

    @Override
    public CompletableFuture<ClientConnector> connect() {
        return CompletableFuture.supplyAsync(() -> {

            eventLoop.register(() -> {
                if(!connector.isConnected()) {
                    connector.connect();
                }
            });
            return connector;
        });
    }

    @Override
    public InetClient onConnect(final Handler<SocketChannel> connectHandler) {
        Assert.checkNull(connectHandler, "Connect handler is null");
        this.connectHandler = connectHandler;
        return this;
    }

    @Override
    public InetClient onRead(final Handler<Buffer> readHandler) {
        Assert.checkNull(readHandler, "Read handler is null");
        this.readHandler = readHandler;
        return this;
    }

    @Override
    public InetClient onWrite(final Handler<Buffer> writeHandler) {
        Assert.checkNull(writeHandler, "Write handler is null");
        this.writeHandler = writeHandler;
        return this;
    }

    @Override
    public InetClient exceptionHandler(final Handler<Throwable> exceptionHandler) {
        Assert.checkNull(exceptionHandler, "Exception handler is null");
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    @Override
    public CompletableFuture<Void> send(final Buffer buffer) {
        if(buffer == null) {
            return CompletableFuture.failedFuture(new ClientException("Buffer is null"));
        }

        return CompletableFuture.runAsync(() -> {
            writePendingTask.add(buffer);
            eventLoop.registerIoChannel(ctx, IOType.WRITE);
        });
    }

    @Override
    public String remoteHost() {
        return connector.getSocketAddress().getHostName();
    }

    @Override
    public int remotePort() {
        return connector.getSocketAddress().getPort();
    }

    @Override
    public boolean isOpen() {
        return connector.isOpen();
    }

    @Override
    public boolean isConnected() {
        return connector.isConnected();
    }

    @Override
    public boolean isClosed() {
        return connector.isClosed();
    }

    @Override
    public void shutdown(final boolean isGraceful) {
        if(isGraceful) {
            if(shutdownHook.isEnabled()) {
                shutdownHook.addShutdownCallback(() -> doClose(true));
            }
            return;
        }

        doClose(false);
    }

    @ThreadSafe
    private void doClose(final boolean isGraceful) {
        if(connector.isClosed()) {
            log.warn("Already closed");
            return;
        }

        log.info("Closing client...");

        try {
            eventLoop.gracefullyShutdown();
            eventLoop.close();
            connector.close();
            log.info("Closed client");
        } catch (IOException e) {
            throw new ClientException("Cannot close server", e);
        }
    }
}