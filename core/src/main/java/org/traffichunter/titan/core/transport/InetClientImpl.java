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
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.util.channel.ChannelContextOutBoundHandler;
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

    private Handler<SocketChannel> connectHandler;
    private Handler<SocketChannel> disconnectHandler;
    private Handler<Buffer> readHandler;
    private Handler<Buffer> writeHandler;
    private Handler<Throwable> exceptionHandler;

    public InetClientImpl(final InetSocketAddress address) {
        Assert.checkNull(address, "Address is null");

        this.connector = ClientConnector.open(address);
        this.eventLoop = new SecondaryNioEventLoop();
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
                eventLoop.register(channelContext, IOType.READ);
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

            @Override
            public void handleDisconnect(final ChannelContext channelContext) {
                disconnectHandler.handle(channelContext.socketChannel());
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
                        eventLoop.register(channelContext, IOType.WRITE);
                        break;
                    }
                }
            }
        });

        eventLoop.exceptionHandler(exceptionHandler);
        eventLoop.register(ChannelContext.create(connector.channel()), IOType.CONNECT);
        eventLoop.start();
        return this;
    }

    @Override
    public CompletableFuture<InetClient> connect() {

        return CompletableFuture.supplyAsync(() -> this);
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
    public InetClient onDisconnect(final Handler<SocketChannel> disconnectHandler) {
        Assert.checkNull(disconnectHandler, "Disconnect handler is null");
        this.disconnectHandler = disconnectHandler;
        return this;
    }

    @Override
    public InetClient exceptionHandler(final Handler<Throwable> exceptionHandler) {
        Assert.checkNull(exceptionHandler, "Exception handler is null");
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    @Override
    public CompletableFuture<InetClient> send(final Buffer buffer) {
        if(buffer == null) {
            return CompletableFuture.failedFuture(new ClientException("Buffer is null"));
        }

        return CompletableFuture.supplyAsync(() -> {
            eventLoop.register(() -> writePendingTask.add(buffer));
            ChannelContext ctx = ChannelContext.create(connector.channel());
            eventLoop.register(ctx, IOType.WRITE);
            return this;
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
            eventLoop.shutdown(isGraceful, 10, TimeUnit.SECONDS);
            connector.close();
            log.info("Closed client");
        } catch (IOException e) {
            throw new ClientException("Cannot close server", e);
        }
    }
}