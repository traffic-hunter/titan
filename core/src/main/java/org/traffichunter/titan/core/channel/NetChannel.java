/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
     * Returns raw transport operations for use inside Titan.
     *
     * <p>Internal operations bypass the channel pipeline. They are used by I/O event loops,
     * pipeline terminals, and protocol handlers that already hold transport-ready bytes, such
     * as an encoded WebSocket frame or encrypted TLS record. Bypassing the pipeline does not
     * imply synchronous execution; scheduling is a separate concern.</p>
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
         * Schedules a write-buffer writability transition through the inbound handler chain.
         * This is independent of selector write interest. Handlers must recheck writability
         * before submitting a write; the event does not reserve capacity.
         */
        void onWritabilityChanged(boolean isWritable);

        /**
         * Completes a pending non-blocking connection on the underlying transport.
         */
        boolean finishConnect() throws IOException;
    }
}
