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

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author yun
 */
public final class ChannelWriteBuffer {

    private static final int DEFAULT_HIGH_WATERMARK = 64 * 1024;
    private static final int DEFAULT_LOW_WATERMARK = 32 * 1024;
    private static final int DEFAULT_MAX_PENDING_BYTES = Integer.MAX_VALUE;

    private final Queue<Buffer> writeBuffer;
    private @Nullable AggregateChannelWriteBufferMetrics metrics;

    private int pendingBytes;

    private final int maxPendingBytes;
    private final int highWatermarkBytes;
    private final int lowWatermarkBytes;

    private boolean isWritable = true;
    private boolean isClosed;

    public ChannelWriteBuffer() {
        this(DEFAULT_MAX_PENDING_BYTES, DEFAULT_HIGH_WATERMARK, DEFAULT_LOW_WATERMARK);
    }

    public ChannelWriteBuffer(int highWatermarkBytes, int lowWatermarkBytes) {
        this(DEFAULT_MAX_PENDING_BYTES, highWatermarkBytes, lowWatermarkBytes);
    }

    public ChannelWriteBuffer(int maxPendingBytes, int highWatermarkBytes, int lowWatermarkBytes) {
        Assert.checkArgument(highWatermarkBytes > lowWatermarkBytes, "highWatermark must be greater than lowerPoint");
        Assert.checkArgument(highWatermarkBytes > 0, "highWatermark must be greater than 0");
        Assert.checkArgument(lowWatermarkBytes > 0, "lowWatermark must be greater than 0");
        Assert.checkArgument(maxPendingBytes > 0, "maxPendingBytes must be greater than 0");

        this.writeBuffer = new ArrayDeque<>();
        this.maxPendingBytes = maxPendingBytes;
        this.highWatermarkBytes = highWatermarkBytes;
        this.lowWatermarkBytes = lowWatermarkBytes;
    }

    ChannelWriteBuffer(
            int maxPendingBytes,
            int highWatermarkBytes,
            int lowWatermarkBytes,
            AggregateChannelWriteBufferMetrics metrics
    ) {
        this(maxPendingBytes, highWatermarkBytes, lowWatermarkBytes);
        attachMetrics(metrics);
    }

    public void append(Buffer buffer) {
        if (isClosed) {
            buffer.release();
            throw new ChannelException("Channel write buffer is closed");
        }
        if(!buffer.hasRemaining()) {
            buffer.release();
            return;
        }

        int contentLength = buffer.length();
        if (contentLength > maxPendingBytes - pendingBytes) {
            buffer.release();
            throw new ChannelException("Channel write buffer is full");
        }

        pendingBytes += contentLength;
        writeBuffer.add(buffer);

        AggregateChannelWriteBufferMetrics currentMetrics = metrics;
        if (currentMetrics != null) {
            currentMetrics.addPendingBytes(buffer.length());
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
     * Call {@link #consume(int)} to advance it; callers must not release or advance it themselves.
     */
    public @Nullable Buffer current() {
        return writeBuffer.peek();
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

        if (!buffer.isReadable()) {
            writeBuffer.remove();
            buffer.release();
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

        if(!writeBuffer.isEmpty()) {
            writeBuffer.forEach(Buffer::release);
        }

        writeBuffer.clear();
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
}
