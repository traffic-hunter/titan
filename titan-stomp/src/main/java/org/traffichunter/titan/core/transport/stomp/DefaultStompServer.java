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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.traffichunter.titan.core.transport.Connector;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.ServerConnector;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.inet.ReadHandler;
import org.traffichunter.titan.core.util.inet.WriteHandler;

/**
 * @author yungwang-o
 */
final class DefaultStompServer implements StompServer {

    private final InetServer inetServer;

    public DefaultStompServer() {
        this(InetServer.open());
    }

    public DefaultStompServer(final InetServer inetServer) {
        this.inetServer = inetServer;
    }

    @Override
    public void start() {
        inetServer.start();
    }

    @Override
    public Future<StompServer> listen() {
        inetServer.listen();
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public Future<StompServer> listen(final int port) {
        inetServer.listen(port);
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public Future<StompServer> listen(final String host, final int port) {
        inetServer.listen(host, port);
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public StompServer onRead(final ReadHandler<byte[]> handler) {
        inetServer.onRead(handler);
        return this;
    }

    @Override
    public StompServer onWrite(final WriteHandler<byte[]> handler) {
        inetServer.onWrite(handler);
        return this;
    }

    @Override
    public int activePort() {
        return inetServer.activePort();
    }

    @Override
    public boolean isStart() {
        return inetServer.isStart();
    }

    @Override
    public boolean isListening() {
        return inetServer.isListening();
    }

    @Override
    public boolean isClosed() {
        return inetServer.isClosed();
    }

    @Override
    public void close() {
        inetServer.close();
    }
}
