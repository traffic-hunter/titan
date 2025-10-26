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
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Assert;

/**
 * @author yungwang-o
 */
@Slf4j
class DefaultClientConnector implements ClientConnector {

    private final SocketChannel socketChannel;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final InetSocketAddress remoteAddress;

    DefaultClientConnector(final InetSocketAddress socketAddress) {
        try {
            this.socketChannel = SocketChannel.open();
            this.socketChannel.configureBlocking(false);

            log.debug("connected to {}", socketAddress);

            this.socketChannel.connect(socketAddress);
            boolean connected = socketChannel.connect(socketAddress);
            if (!connected) {
                log.debug("Connection in progress to {}", socketAddress);
            } else {
                log.info("Connection immediately established to {}", socketAddress);
            }

            this.remoteAddress = socketAddress;
        } catch (IOException e) {
            log.error("Failed to connect = {}, reason = {}", socketAddress, e.getMessage());
            throw new TransportException(e);
        }
    }

    @Override
    public SocketChannel channel() {
        Assert.checkState(isOpen(), "Not open socketChannel");

        return socketChannel;
    }

    @Override
    public boolean isFinishConnect() {
        try {
            return socketChannel.finishConnect();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean isConnectPending() {
        return socketChannel.isConnectionPending();
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return remoteAddress;
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen() && !closed.get();
    }

    @Override
    public boolean isConnected() {
        return socketChannel.isConnected();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if(closed.compareAndSet(false, true)) {
            try {
                log.info("Closing connection to {}", remoteAddress);
                socketChannel.close();
            } catch (IOException e) {
                log.warn("Failed to close connection to {}", remoteAddress);
            } finally {
                try {
                    if (socketChannel.isOpen()) {
                        socketChannel.close();
                    }
                } catch (IOException ignored) { }
            }
        }
    }
}