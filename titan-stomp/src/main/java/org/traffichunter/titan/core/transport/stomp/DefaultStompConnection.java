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
package org.traffichunter.titan.core.transport.stomp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.traffichunter.titan.core.codec.stomp.StompFrame;

/**
 * @author yungwang-o
 */
public final class DefaultStompConnection implements StompConnection {

    private final SocketChannel socketChannel;

    private final ByteBuffer buffer;

    private final StompFrame stompFrame;

    public DefaultStompConnection(final SocketChannel socketChannel,
                                  final ByteBuffer buffer,
                                  final StompFrame stompFrame) {

        this.socketChannel = socketChannel;
        this.buffer = buffer;
        this.stompFrame = stompFrame;
    }

    @Override
    public void open() throws IOException {
        if(socketChannel.isOpen()) {
            throw new IOException("Already opened");
        }

    }

    @Override
    public void send() {

    }

    @Override
    public void connect(final InetAddress address, final int port) throws IOException {
        boolean isConnected = socketChannel.connect(new InetSocketAddress(address, port));

        if(isConnected) {

        }
    }

    @Override
    public void disconnect() throws IOException {
        close();
    }

    @Override
    public void close() throws IOException {
        socketChannel.close();
    }
}
