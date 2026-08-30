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
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author yun
 */
public final class ChannelWriteBuffer {

    /**
     * default upperPoint and lowerPoint
     */
    private static final int DEFAULT_HIGH_WATERMARK = 64 * 1024;
    private static final int DEFAULT_LOW_WATERMARK = 32 * 1024;

    private final Queue<Buffer> writeBuffer;
    private @Nullable AggregateChannelWriteBufferMetrics metrics;

    private int pendingBytes;

    private final int highWatermark;
    private final int lowWatermark;

    private boolean isWritable = true;
    private boolean isClosed;

    public ChannelWriteBuffer() {
        this(DEFAULT_HIGH_WATERMARK, DEFAULT_LOW_WATERMARK);
    }

    public ChannelWriteBuffer(int highWatermark, int lowWatermark) {
        Assert.checkArgument(highWatermark > lowWatermark, "highWatermark must be greater than lowerPoint");
        Assert.checkArgument(highWatermark > 0, "highWatermark must be greater than 0");
        Assert.checkArgument(lowWatermark > 0, "lowWatermark must be greater than 0");

        this.writeBuffer = new ArrayDeque<>();
        this.highWatermark = highWatermark;
        this.lowWatermark = lowWatermark;
    }

    ChannelWriteBuffer(
            int highWatermark,
            int lowWatermark,
            AggregateChannelWriteBufferMetrics metrics
    ) {
        this(highWatermark, lowWatermark);
        attachMetrics(metrics);
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

    public void add(Buffer buffer) {
        if (isClosed) {
            throw new ChannelException("Channel write buffer is closed");
        }
        if(!buffer.hasRemaining()) {
            buffer.release();
            return;
        }

        writeBuffer.add(buffer);

        pendingBytes += buffer.length();
        AggregateChannelWriteBufferMetrics currentMetrics = metrics;
        if (currentMetrics != null) {
            currentMetrics.addPendingBytes(buffer.length());
        }
        if(isWritable && pendingBytes > highWatermark) {
            isWritable = false;
            if (currentMetrics != null) {
                currentMetrics.becameNonWritable();
            }
        }

    }

    public boolean isEmpty() {
        return writeBuffer.isEmpty();
    }

    public @Nullable Buffer current() {
        return writeBuffer.peek();
    }

    @CanIgnoreReturnValue
    public @Nullable Buffer poll() {
        if(writeBuffer.isEmpty()) {
            return null;
        }

        Buffer buffer = writeBuffer.poll();

        int remainingBytes = buffer.length();
        pendingBytes -= remainingBytes;
        AggregateChannelWriteBufferMetrics currentMetrics = metrics;
        if (currentMetrics != null) {
            currentMetrics.removePendingBytes(remainingBytes);
        }
        if(!isWritable && pendingBytes < lowWatermark) {
            isWritable = true;
            if (currentMetrics != null) {
                currentMetrics.becameWritable();
            }
        }

        return buffer;
    }

    public boolean isWritable() {
        return isWritable;
    }

    public int pendingBytes() {
        return pendingBytes;
    }

    public int highWatermark() {
        return highWatermark;
    }

    public int lowWatermark() {
        return lowWatermark;
    }

    void progress(int bytes) {
        Assert.checkArgument(bytes >= 0, "bytes must not be negative");
        Assert.checkArgument(bytes <= pendingBytes, "bytes must not exceed pending bytes");
        pendingBytes -= bytes;
        AggregateChannelWriteBufferMetrics currentMetrics = metrics;
        if (currentMetrics != null) {
            currentMetrics.removePendingBytes(bytes);
        }
        if (!isWritable && pendingBytes < lowWatermark) {
            isWritable = true;
            if (currentMetrics != null) {
                currentMetrics.becameWritable();
            }
        }
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
}
