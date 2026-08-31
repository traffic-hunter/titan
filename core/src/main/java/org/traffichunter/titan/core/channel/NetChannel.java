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
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.concurrent.TimeUnit;

/**
 * Client-side or accepted server-side network connection.
 *
 * <p>{@code NetChannel} is the pipeline-facing data channel. It initiates connections and
 * sends outbound data through its owning {@link IOEventLoop}. Raw reads, flushes, and selector
 * operations remain behind {@link Internal}. Server transports also use this type for accepted
 * child connections after the listening {@link NetServerChannel} accepts them.</p>
 *
 * @author yun
 */
public interface NetChannel extends Channel {

    static NetChannel open(ChannelHandShakeEventListener initializer) throws IOException {
        return new NewIONetChannel(initializer);
    }

    @Override
    <T> NetChannel setOption(SocketOption<T> option, T value);

    ChannelPromise connect(String host, int port, long timeOut, TimeUnit timeUnit);

    /**
     * Returns raw transport operations intended for Titan's internal channel machinery.
     *
     * <p>Internal operations bypass the channel pipeline. They are used by I/O event loops,
     * pipeline terminals, and protocol handlers that already hold transport-ready bytes, such
     * as an encoded WebSocket frame or encrypted TLS record. This distinction is independent
     * of scheduling: an internal operation is not the synchronous counterpart of a public
     * channel operation.</p>
     */
    Internal internal();

    /**
     * Starts or completes a non-blocking socket connection.
     */
    @CanIgnoreReturnValue
    ChannelPromise connect(InetSocketAddress remote, long timeOut, TimeUnit timeUnit);

    @CanIgnoreReturnValue
    ChannelPromise disconnect();

    @CanIgnoreReturnValue
    ChannelPromise write(Buffer buffer);

    /**
     * Queues the buffer and attempts to write queued bytes to the socket.
     */
    @CanIgnoreReturnValue
    ChannelPromise writeAndFlush(Buffer buffer);

    boolean isConnected();

    boolean isWritable();

    /**
     * Raw transport operations that bypass the inbound and outbound channel pipelines.
     *
     * <p>Callers are responsible for invoking these operations from the appropriate channel
     * execution context. A returned value or normal method completion only means that the
     * transport operation was attempted or queued; it does not mean that network I/O has
     * completed.</p>
     */
    interface Internal {

        /**
         * Reads raw bytes from the underlying transport.
         */
        int read(Buffer buffer);

        /**
         * Queues transport-ready bytes without entering the outbound pipeline.
         */
        void write(Buffer buffer);

        /**
         * Queues transport-ready bytes and attempts to flush them without entering the pipeline.
         */
        void writeAndFlush(Buffer buffer);

        /**
         * Attempts to flush queued raw bytes to the underlying transport.
         */
        void flush();

        /**
         * Updates write-readiness interest for the underlying transport.
         */
        void onWritabilityChanged(boolean isWritable);

        /**
         * Completes a pending non-blocking connection on the underlying transport.
         */
        boolean finishConnect() throws IOException;
    }
}
