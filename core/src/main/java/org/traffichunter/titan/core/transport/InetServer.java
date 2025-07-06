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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.traffichunter.titan.core.event.EventLoop;
import org.traffichunter.titan.core.util.inet.InetConstants;
import org.traffichunter.titan.core.util.inet.ReadHandler;
import org.traffichunter.titan.core.util.inet.WriteHandler;

/**
 * @author yungwang-o
 */
public interface InetServer {

    static InetServer open() {
        return new InetServerImpl(ServerConnector.open(), EventLoop.single());
    }

    @CanIgnoreReturnValue
    CompletableFuture<InetServer> listen();

    @CanIgnoreReturnValue
    default CompletableFuture<InetServer> listen(int port) {
        return listen(InetConstants.UNKNOWN_HOST, port);
    }

    @CanIgnoreReturnValue
    default CompletableFuture<InetServer> listen(String host, int port) {
        return listen(new InetSocketAddress(host, port));
    }

    @CanIgnoreReturnValue
    CompletableFuture<InetServer> listen(InetSocketAddress address);

    @CanIgnoreReturnValue
    InetServer exceptionHandler(Consumer<Throwable> handler);

    InetServer onRead(ReadHandler<byte[]> handler);

    InetServer onWrite(WriteHandler<byte[]> handler);

    int activePort();

    boolean isListening();

    boolean isClosed();

    CompletableFuture<Void> shutdown(boolean isGraceful);

    CompletableFuture<Void> close();

    class ServerException extends RuntimeException {

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
