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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.traffichunter.titan.core.util.management.ChannelWriteBufferMbean;
import org.traffichunter.titan.core.util.management.ChannelWriteBufferMbeans;

/**
 * Aggregates write buffer state without retaining channel references.
 *
 * @author yun
 */
final class AggregateChannelWriteBufferMetrics implements ChannelWriteBufferMbean {

    private static final AggregateChannelWriteBufferMetrics PROCESS_WIDE = createProcessWide();

    private final AtomicInteger activeBuffers = new AtomicInteger();
    private final AtomicLong pendingBytes = new AtomicLong();
    private final AtomicInteger nonWritableBuffers = new AtomicInteger();

    static AggregateChannelWriteBufferMetrics processWide() {
        return PROCESS_WIDE;
    }

    void open(long initialPendingBytes, boolean writable) {
        activeBuffers.incrementAndGet();
        pendingBytes.addAndGet(initialPendingBytes);
        if (!writable) {
            nonWritableBuffers.incrementAndGet();
        }
    }

    void close(long remainingBytes, boolean writable) {
        pendingBytes.addAndGet(-remainingBytes);
        if (!writable) {
            nonWritableBuffers.decrementAndGet();
        }
        activeBuffers.decrementAndGet();
    }

    void addPendingBytes(long bytes) {
        pendingBytes.addAndGet(bytes);
    }

    void removePendingBytes(long bytes) {
        pendingBytes.addAndGet(-bytes);
    }

    void becameNonWritable() {
        nonWritableBuffers.incrementAndGet();
    }

    void becameWritable() {
        nonWritableBuffers.decrementAndGet();
    }

    @Override
    public int getActiveBuffers() {
        return activeBuffers.get();
    }

    @Override
    public long getPendingBytes() {
        return pendingBytes.get();
    }

    @Override
    public int getNonWritableBuffers() {
        return nonWritableBuffers.get();
    }

    private static AggregateChannelWriteBufferMetrics createProcessWide() {
        AggregateChannelWriteBufferMetrics metrics = new AggregateChannelWriteBufferMetrics();
        ChannelWriteBufferMbeans.register("process", metrics);
        return metrics;
    }
}
