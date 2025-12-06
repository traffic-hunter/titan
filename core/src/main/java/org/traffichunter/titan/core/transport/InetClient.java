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
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yungwang-o
 */
public interface InetClient {

    static InetClient open(String host, int port) {
        return new InetClientImpl(new InetSocketAddress(host, port));
    }

    static InetClient open(InetSocketAddress address) {
        return new InetClientImpl(address);
    }

    @CanIgnoreReturnValue
    InetClient setOption(ClientOptions options);

    @CanIgnoreReturnValue
    InetClient start();

    @CanIgnoreReturnValue
    CompletableFuture<ClientConnector> connect();

    @CanIgnoreReturnValue
    InetClient onConnect(Handler<SocketChannel> connectHandler);

    @CanIgnoreReturnValue
    InetClient onRead(Handler<Buffer> readHandler);

    @CanIgnoreReturnValue
    InetClient onWrite(Handler<Buffer> writeHandler);

    @CanIgnoreReturnValue
    InetClient exceptionHandler(Handler<Throwable> exceptionHandler);

    @CanIgnoreReturnValue
    CompletableFuture<Void> send(Buffer buffer);

    String remoteHost();

    int remotePort();

    boolean isOpen();

    boolean isConnected();

    boolean isClosed();

    void shutdown(boolean isGraceful);

    default void close() { shutdown(false); }

    class ClientException extends TransportException {

        public ClientException() {}

        public ClientException(String message) { super(message); }

        public ClientException(String message, Throwable cause) { super(message, cause); }

        public ClientException(Throwable cause) { super(cause); }

        public ClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
