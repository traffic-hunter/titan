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

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.ChannelWriteException.Reason;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Buffers outbound data that could not be written to a channel immediately.
 *
 * <p>Tracks pending bytes and reports pressure through high and low watermarks. It never refuses
 * a write for lack of space: the writer is expected to consult {@link #isWritable()} on the
 * channel's event loop and hold back itself, so an unchecked writer grows this buffer without
 * bound.</p>
 *
 * @author yun
 */
public final class ChannelWriteBuffer {

    private static final int DEFAULT_HIGH_WATERMARK = 64 * 1024;
    private static final int DEFAULT_LOW_WATERMARK = 32 * 1024;

    private final Queue<Entry> writeBuffer;
    private @Nullable AggregateChannelWriteBufferMetrics metrics;

    private int pendingBytes;

    private final int highWatermarkBytes;
    private final int lowWatermarkBytes;

    private volatile boolean isWritable = true;
    private boolean isClosed;

    private boolean headRequestStarted;

    public ChannelWriteBuffer() {
        this(DEFAULT_HIGH_WATERMARK, DEFAULT_LOW_WATERMARK);
    }

    public ChannelWriteBuffer(int highWatermarkBytes, int lowWatermarkBytes) {
        Assert.checkArgument(highWatermarkBytes > lowWatermarkBytes, "highWatermark must be greater than lowerPoint");
        Assert.checkArgument(highWatermarkBytes > 0, "highWatermark must be greater than 0");
        Assert.checkArgument(lowWatermarkBytes > 0, "lowWatermark must be greater than 0");

        this.writeBuffer = new ArrayDeque<>();
        this.highWatermarkBytes = highWatermarkBytes;
        this.lowWatermarkBytes = lowWatermarkBytes;
    }

    ChannelWriteBuffer(AggregateChannelWriteBufferMetrics metrics) {
        this(DEFAULT_HIGH_WATERMARK, DEFAULT_LOW_WATERMARK, metrics);
    }

    ChannelWriteBuffer(int highWatermarkBytes, int lowWatermarkBytes, AggregateChannelWriteBufferMetrics metrics) {
        this(highWatermarkBytes, lowWatermarkBytes);
        attachMetrics(metrics);
    }

    /**
     * Appends one request made of a single buffer.
     *
     * @see #append(List, ChannelPromise)
     */
    public void append(Buffer buffer, ChannelPromise promise) {
        append(List.of(buffer), promise);
    }

    /**
     * Appends one request and takes ownership of every buffer in it.
     *
     * <p>The promise completes once the socket has taken every byte of every buffer, so it rides
     * on the last non-empty one. A write to a closed buffer has all of its buffers released and
     * its promise failed with {@link ChannelWriteException.Reason#NOT_SENT}; nothing is thrown.
     * The list itself is not retained.</p>
     *
     * @param buffers the request's buffers in wire order
     * @param promise completion for the whole request
     */
    public void append(List<Buffer> buffers, ChannelPromise promise) {
        if (isClosed) {
            refuse(buffers, promise, Reason.NOT_SENT, "Channel write buffer is closed");
            return;
        }

        long contentLength = 0;
        int lastReadable = -1;
        for (int i = 0; i < buffers.size(); i++) {
            Buffer buffer = buffers.get(i);
            if (buffer.hasRemaining()) {
                contentLength += buffer.length();
                lastReadable = i;
            }
        }
        if (lastReadable < 0) {
            buffers.forEach(Buffer::release);
            // Nothing to write, so the request is already through.
            promise.success();
            return;
        }
        for (int i = 0; i < buffers.size(); i++) {
            Buffer buffer = buffers.get(i);
            if (!buffer.hasRemaining()) {
                buffer.release();
                continue;
            }
            writeBuffer.add(new Entry(i == lastReadable ? promise : null, buffer));
        }
        pendingBytes += (int) contentLength;

        AggregateChannelWriteBufferMetrics currentMetrics = metrics;
        if (currentMetrics != null) {
            currentMetrics.addPendingBytes((int) contentLength);
        }
        if(isWritable && pendingBytes > highWatermarkBytes) {
            isWritable = false;
            if (currentMetrics != null) {
                currentMetrics.becameNonWritable();
            }
        }
    }

    public boolean isEmpty() {
        return writeBuffer.isEmpty();
    }

    /**
     * Returns the current buffer as a borrowed reference for socket I/O.
     *
     * <p>Call {@link #consume(int)} to advance it; callers must not release or advance it
     * themselves.</p>
     *
     * @return the first pending buffer, or {@code null} when the queue is empty
     */
    public @Nullable Buffer current() {
        Entry head = writeBuffer.peek();
        return head == null ? null : head.buffer;
    }

    /**
     * Consumes bytes successfully written from the current buffer.
     *
     * <p>Advances its reader index, updates pending-byte metrics and writability, and releases
     * the buffer once fully consumed. The count must not exceed the current buffer's readable
     * bytes. Zero leaves the queue unchanged. Call only from the owning channel event loop.</p>
     *
     * @param bytes number of bytes written to the socket
     * @throws IllegalArgumentException if the count is negative or exceeds the current buffer
     * @throws ChannelException if the write buffer is closed
     */
    public void consume(int bytes) {
        if (isClosed) {
            throw new ChannelException("Channel write buffer is closed");
        }
        Assert.checkArgument(bytes >= 0, "bytes must not be negative");
        if (bytes == 0) {
            return;
        }
        Buffer buffer = current();
        if (buffer == null) {
            throw new IllegalArgumentException("bytes must not exceed current buffer readable bytes");
        }
        Assert.checkArgument(bytes <= buffer.length(), "bytes must not exceed current buffer readable bytes");

        buffer.skipBytes(bytes);
        pendingBytes -= bytes;
        AggregateChannelWriteBufferMetrics currentMetrics = metrics;
        if (currentMetrics != null) {
            currentMetrics.removePendingBytes(bytes);
        }
        if(!isWritable && pendingBytes < lowWatermarkBytes) {
            isWritable = true;
            if (currentMetrics != null) {
                currentMetrics.becameWritable();
            }
        }

        headRequestStarted = true;

        if (!buffer.isReadable()) {
            Entry head = writeBuffer.remove();
            buffer.release();
            if (head.promise != null) {
                // Every buffer of this request is through, so the request itself is written.
                headRequestStarted = false;
                head.promise.success();
            }
        }
    }

    public boolean isWritable() {
        return isWritable;
    }

    public int pendingBytes() {
        return pendingBytes;
    }

    public int highWatermark() {
        return highWatermarkBytes;
    }

    public int lowWatermark() {
        return lowWatermarkBytes;
    }

    public void close() {
        if (isClosed) {
            return;
        }
        isClosed = true;

        discardPendingWrites();
        int remainingBytes = pendingBytes;
        pendingBytes = 0;
        AggregateChannelWriteBufferMetrics currentMetrics = metrics;
        if (currentMetrics != null) {
            currentMetrics.close(remainingBytes, isWritable);
            metrics = null;
        }
        isWritable = false;
    }

    void attachMetrics(AggregateChannelWriteBufferMetrics metrics) {
        if (isClosed) {
            throw new ChannelException("Cannot attach metrics to a closed channel write buffer");
        }
        if (this.metrics == metrics) {
            return;
        }
        if (this.metrics != null) {
            throw new ChannelException("Channel write buffer metrics are already attached");
        }

        this.metrics = metrics;
        metrics.open(pendingBytes, isWritable);
    }

    /**
     * Releases every queued buffer and fails the promise of each request left unwritten.
     *
     * <p>The request at the head may have been part-written, which the peer cannot be asked
     * about. Requests behind it never reached the socket.</p>
     */
    private void discardPendingWrites() {
        boolean started = headRequestStarted;
        Entry entry;
        while ((entry = writeBuffer.poll()) != null) {
            entry.buffer.release();
            if (entry.promise != null) {
                entry.promise.fail(started
                        ? new ChannelWriteException(Reason.UNKNOWN, "Channel closed while the write was in progress")
                        : new ChannelWriteException(Reason.NOT_SENT, "Channel closed before the write started"));
                started = false;
            }
        }
        headRequestStarted = false;
    }

    private static void refuse(List<Buffer> buffers, ChannelPromise promise, Reason reason, String message) {
        buffers.forEach(Buffer::release);
        promise.fail(new ChannelWriteException(reason, message));
    }


    /** One queued buffer. Only the last buffer of a request carries the request's promise. */
    static final class Entry {

        final @Nullable ChannelPromise promise;
        final Buffer buffer;

        Entry(@Nullable ChannelPromise promise, Buffer buffer) {
            this.promise = promise;
            this.buffer = buffer;
        }
    }
}
