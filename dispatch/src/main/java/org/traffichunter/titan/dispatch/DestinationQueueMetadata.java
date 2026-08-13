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
package org.traffichunter.titan.dispatch;

import org.traffichunter.titan.core.util.concurrent.ThreadSafe;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe live metadata for one destination queue.
 *
 * <p>The immutable fields identify the queue and define its admission limits.
 * Pending bytes include reservations made by concurrent producers before
 * their messages become visible in the underlying queue. This conservative
 * accounting prevents concurrent publishers from overshooting the byte limit.</p>
 *
 * <p>Mutation methods have package visibility so only the queue implementation
 * can reserve or release pending bytes. Callers receiving this object can inspect
 * current state without being able to corrupt its accounting.</p>
 *
 * @author yun
 */
@ThreadSafe
public final class DestinationQueueMetadata {

    private volatile String destination;
    private final Instant createdAt;
    private final long maxPendingBytes;
    private final AtomicLong pendingBytes = new AtomicLong();
    private final AtomicBoolean paused = new AtomicBoolean();

    public DestinationQueueMetadata(
            String destination,
            Instant createdAt,
            long maxPendingBytes
    ) {
        if (destination.isBlank()) {
            throw new IllegalArgumentException("Destination must not be blank");
        }
        if (maxPendingBytes <= 0) {
            throw new IllegalArgumentException("Max pending bytes must be greater than zero");
        }
        this.destination = destination;
        this.createdAt = createdAt;
        this.maxPendingBytes = maxPendingBytes;
    }

    boolean tryReserve(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Pending bytes must not be negative");
        }
        return reserveBytes(bytes);
    }

    void release(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Released bytes must not be negative");
        }

        pendingBytes.updateAndGet(current -> {
            if (current < bytes) {
                throw new IllegalStateException("Released bytes exceed pending bytes");
            }
            return current - bytes;
        });
    }

    void paused(boolean paused) {
        this.paused.set(paused);
    }

    void destination(String destination) {
        if (destination.isBlank()) {
            throw new IllegalArgumentException("Destination must not be blank");
        }
        this.destination = destination;
    }

    public String getDestination() {
        return destination;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getMaxPendingBytes() {
        return maxPendingBytes;
    }

    public long getPendingBytes() {
        return pendingBytes.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean isSaturated() {
        return getPendingBytes() >= maxPendingBytes;
    }

    private boolean reserveBytes(long bytes) {
        while (true) {
            long current = pendingBytes.get();
            if (bytes > maxPendingBytes - current) {
                return false;
            }
            if (pendingBytes.compareAndSet(current, current + bytes)) {
                return true;
            }
        }
    }
}
