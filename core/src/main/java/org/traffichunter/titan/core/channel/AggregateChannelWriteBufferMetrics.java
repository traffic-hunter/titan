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
