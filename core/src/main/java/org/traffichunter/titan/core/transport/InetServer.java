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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.inet.ReadHandler;

/**
 * @author yungwang-o
 */
public interface InetServer {

    static InetServer open(String host, int port) {
        return InetServer.open(new InetSocketAddress(host, port));
    }

    static InetServer open(InetSocketAddress address) {
        return new InetServerImpl(ServerConnector.open(address));
    }

    void start();

    @CanIgnoreReturnValue
    CompletableFuture<InetServer> listen();

    @CanIgnoreReturnValue
    InetServer exceptionHandler(Handler<Throwable> handler);

    @CanIgnoreReturnValue
    InetServer onConnect(Handler<SocketChannel> handler);

    @CanIgnoreReturnValue
    InetServer onRead(ReadHandler readHandler);

    String host();

    int activePort();

    boolean isStart();

    boolean isListening();

    boolean isClosed();

    void shutdown(boolean isGraceful);

    default void close() { shutdown(false); }

    class ServerException extends TransportException {

        public ServerException() {
        }

        public ServerException(final String message) {
            super(message);
        }

        public ServerException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public ServerException(final Throwable cause) {
            super(cause);
        }

        public ServerException(final String message, final Throwable cause, final boolean enableSuppression,
                               final boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
