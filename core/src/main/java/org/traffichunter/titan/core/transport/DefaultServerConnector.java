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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.traffichunter.titan.core.util.IdGenerator;

/**
 * @author yungwang-o
 */
public class DefaultServerConnector implements ServerConnector {

    private ServerSocketChannel serverSocketChannel;

    private Selector selector;

    private final int port;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final String sessionId;

    public DefaultServerConnector(final int port){
        this.port = port;
        this.sessionId = IdGenerator.generate();
    }

    @Override
    public void open() throws IOException {
        if (serverSocketChannel == null || closed.get()) {
            return;
        }

        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public SocketChannel accept(final boolean isBlocking) throws IOException {
        if(!isOpen()) {
            throw new IllegalStateException("connector is closed or not open");
        }

        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(isBlocking);
        return socketChannel;
    }

    @Override
    public ServerSocketChannel serverSocketChannel() {
        if(!isOpen()) {
            throw new IllegalStateException("connector is closed or not open");
        }

        return serverSocketChannel;
    }

    @Override
    public Selector selector() {
        if(!selector.isOpen() || selector == null) {
            throw new IllegalStateException("selector is not open");
        }

        return selector;
    }

    @Override
    public SelectionKey selectionKey() {
        if(!selector.isOpen() || selector == null) {
            throw new IllegalStateException("selector is not open");
        }

        return serverSocketChannel.keyFor(selector);
    }

    @Override
    public String sessionId() {
        return this.sessionId;
    }

    @Override
    public boolean isOpen() {
        return serverSocketChannel != null &&
                selector != null &&
                serverSocketChannel.isOpen() &&
                selector.isOpen() &&
                !closed.get();
    }

    @Override
    public boolean isClosed() {
        return serverSocketChannel != null && (!serverSocketChannel.isOpen() || closed.get());
    }

    @Override
    public void close() throws IOException {
        if(serverSocketChannel == null) {
            return;
        }

        if(closed.compareAndSet(false, true)) {
            selector.close();
            serverSocketChannel.close();
        }
    }
}