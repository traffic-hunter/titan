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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author yun
 */
public final class ChannelWriteBuffer implements Closeable {

    /**
     * default upperPoint and lowerPoint
     */
    private static final int DEFAULT_UPPER_POINT = 64 * 1024;
    private static final int DEFAULT_LOWER_POINT = 32 * 1024;

    private final Queue<Buffer> writeBuffer;
    private final NetChannel channel;

    private static final AtomicIntegerFieldUpdater<ChannelWriteBuffer> PENDING_BYTES_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(ChannelWriteBuffer.class, "pendingBytes");
    private volatile int pendingBytes;

    private final int upperPoint;
    private final int lowerPoint;

    private volatile boolean isWritable = true;

    public ChannelWriteBuffer(NetChannel channel) {
        this(channel, DEFAULT_UPPER_POINT, DEFAULT_LOWER_POINT);
    }

    public ChannelWriteBuffer(NetChannel channel, int upperPoint, int lowerPoint) {
        Assert.checkArgument(upperPoint > lowerPoint, "upperPoint must be greater than lowerPoint");
        Assert.checkArgument(upperPoint > 0, "upperPoint must be greater than 0");
        Assert.checkArgument(lowerPoint > 0, "lowerPoint must be greater than 0");

        this.writeBuffer = new ArrayDeque<>();
        this.channel = channel;
        this.upperPoint = upperPoint;
        this.lowerPoint = lowerPoint;
    }

    public void add(@NonNull Buffer buffer) {
        if(!buffer.hasRemaining()) {
            return;
        }

        writeBuffer.add(buffer);

        int pendingBytes = PENDING_BYTES_UPDATER.addAndGet(this, buffer.length());
        if(isWritable && pendingBytes > upperPoint) {
            isWritable = false;
            notifyWritable(false);
        }
    }

    public boolean isEmpty() {
        return writeBuffer.isEmpty();
    }

    public Buffer current() {
        return writeBuffer.peek();
    }

    @CanIgnoreReturnValue
    public @Nullable Buffer poll() {
        if(writeBuffer.isEmpty()) {
            return null;
        }

        Buffer buffer = writeBuffer.poll();
        if(buffer == null) {
            return null;
        }

        int pendingBytes = PENDING_BYTES_UPDATER.addAndGet(this, -buffer.length());
        if(!isWritable && pendingBytes < lowerPoint) {
            isWritable = true;
            notifyWritable(true);
        }

        return buffer;
    }

    private boolean isWritable() {
        return isWritable;
    }

    private void notifyWritable(boolean isWritable) {

    }

    @Override
    public void close() throws IOException {
        writeBuffer.clear();
        PENDING_BYTES_UPDATER.set(this, 0);
        isWritable = false;
    }
}
