/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.channel;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Client-side or accepted server-side network connection.
 *
 * <p>{@code NetChannel} is the data-carrying channel type. It can initiate connects, read
 * bytes into {@link Buffer}, enqueue outbound buffers, and flush them through its owning
 * {@link IOEventLoop}. Server transports also use this same type for accepted child
 * connections after the listening {@link NetServerChannel} accepts them.</p>
 *
 * @author yun
 */
public interface NetChannel extends Channel {

    static NetChannel open(ChannelHandShakeEventListener initializer) throws IOException {
        return new NewIONetChannel(initializer);
    }

    @Override
    <T> NetChannel setOption(SocketOption<T> option, T value);

    default Promise<Void> connect(String host, int port, long timeOut, TimeUnit timeUnit) {
        return connect(new InetSocketAddress(host, port), timeOut, timeUnit);
    }

    /**
     * Returns the synchronous transport operations used by the owning I/O event loop and
     * protocol handlers that must bypass the outbound chain.
     */
    Internal internal();

    /**
     * Starts or completes a non-blocking socket connection.
     */
    @CanIgnoreReturnValue
    default Promise<Void> connect(InetSocketAddress remote, long timeOut, TimeUnit timeUnit) {
        return execute(() -> {
            try {
                internal().connect(remote, timeOut, timeUnit);
            } catch (IOException e) {
                throw new ChannelException("Failed to connect to " + remote, e);
            }
        });
    }

    @CanIgnoreReturnValue
    default Promise<Void> disconnect() {
        return execute(internal()::disconnect);
    }

    /**
     * Reads available bytes without blocking.
     */
    @CanIgnoreReturnValue
    default Promise<Integer> read(Buffer buffer) {
        return execute(() -> internal().read(buffer));
    }

    @CanIgnoreReturnValue
    default Promise<Void> write(Buffer buffer) {
        return execute(() -> chain().processChannelWrite(this, buffer));
    }

    /**
     * Queues the buffer and attempts to write queued bytes to the socket.
     */
    @CanIgnoreReturnValue
    default Promise<Void> writeAndFlush(Buffer buffer) {
        return execute(() -> {
            chain().processChannelWrite(this, buffer);
            internal().flush();
        });
    }

    @CanIgnoreReturnValue
    default Promise<Void> flush() {
        return execute(internal()::flush);
    }

    default Promise<Void> onWritabilityChanged(boolean isWritable) {
        return execute(() -> internal().onWritabilityChanged(isWritable));
    }

    /**
     * Completes a pending non-blocking connect from the owning event-loop thread.
     */
    default Promise<Boolean> finishConnect() {
        return execute(() -> {
            try {
                return internal().finishConnect();
            } catch (IOException e) {
                throw new ChannelException("Failed to finish channel connection", e);
            }
        });
    }

    boolean isConnected();

    private Promise<Void> execute(Runnable task) {
        IOEventLoop eventLoop = eventLoop();
        if (!eventLoop.inEventLoop()) {
            return eventLoop.submit(task);
        }

        Promise<Void> result = Promise.newPromise(eventLoop);
        try {
            task.run();
            result.success();
        } catch (Throwable error) {
            result.fail(error);
        }
        return result;
    }

    private <T> Promise<T> execute(Callable<T> task) {
        IOEventLoop eventLoop = eventLoop();
        if (!eventLoop.inEventLoop()) {
            return eventLoop.submit(task);
        }

        Promise<T> result = Promise.newPromise(eventLoop);
        try {
            result.success(task.call());
        } catch (Throwable error) {
            result.fail(error);
        }
        return result;
    }

    /**
     * Synchronous low-level channel operations.
     *
     * <p>These methods execute immediately and are intended for the channel's I/O event-loop
     * thread. A successful call means the operation was attempted or queued; it does not mean
     * that the peer received the bytes.</p>
     */
    interface Internal {

        void connect(InetSocketAddress remote, long timeOut, TimeUnit timeUnit) throws IOException;

        void disconnect();

        int read(Buffer buffer);

        void write(Buffer buffer);

        void writeAndFlush(Buffer buffer);

        void flush();

        void onWritabilityChanged(boolean isWritable);

        boolean finishConnect() throws IOException;
    }
}
