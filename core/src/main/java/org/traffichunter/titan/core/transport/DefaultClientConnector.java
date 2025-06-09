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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yungwang-o
 */
public class DefaultClientConnector implements ClientConnector {

    private final SocketChannel socketChannel;

    private final InetSocketAddress socketAddress;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    DefaultClientConnector(final InetSocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        try {
            this.socketChannel = SocketChannel.open();
            this.socketChannel.configureBlocking(false);
            this.socketChannel.connect(socketAddress);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SocketChannel socketChannel() {
        if(!isOpen()) {
            throw new IllegalStateException("Client socket is not open");
        }

        return socketChannel;
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
    public void close() throws IOException {
        if(closed.compareAndSet(false, true)) {
            socketChannel.close();
        }
    }
}
