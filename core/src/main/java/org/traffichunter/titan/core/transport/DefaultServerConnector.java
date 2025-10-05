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
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;

/**
 * @author yungwang-o
 */
@Slf4j
public class DefaultServerConnector implements ServerConnector {

    private final ServerSocketChannel serverSocketChannel;

    private final InetSocketAddress address;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final String sessionId;

    DefaultServerConnector(final InetSocketAddress address) {
        this.sessionId = IdGenerator.uuid();
        try {
            this.address = address;

            if(this.address.isUnresolved()) {
                log.error("Address is unresolved");
                throw new IllegalStateException("Address is unresolved");
            }

            log.info("Connected on host = {}, port = {}", address.getHostName(), address.getPort());

            this.serverSocketChannel = ServerSocketChannel.open();
            this.serverSocketChannel.configureBlocking(false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String host() { return address.getHostName(); }

    @Override
    public int port() { return address.getPort(); }

    @Override
    public void bind() throws IOException {
        serverSocketChannel.bind(address);
    }

    @Override
    public ServerSocketChannel serverSocketChannel() {
        if(!isOpen()) {
            throw new IllegalStateException("connector is closed or not open");
        }

        return serverSocketChannel;
    }

    @Override
    public String sessionId() {
        return this.sessionId;
    }

    @Override
    public boolean isOpen() {
        return serverSocketChannel != null &&
                serverSocketChannel.isOpen() &&
                !closed.get();
    }

    @Override
    public boolean isClosed() {
        return serverSocketChannel != null && (!serverSocketChannel.isOpen() || closed.get());
    }

    @Override
    @ThreadSafe
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {

            if (serverSocketChannel == null) {
                return;
            }

            serverSocketChannel.close();
        }
    }
}