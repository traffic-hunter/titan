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

    private final String destination;
    private final Instant createdAt;
    private final long maxPendingBytes;
    private final long resumePendingBytes;
    private final AtomicLong pendingBytes = new AtomicLong();
    private final AtomicBoolean paused = new AtomicBoolean();

    public DestinationQueueMetadata(
            String destination,
            Instant createdAt,
            long maxPendingBytes
    ) {
        this(destination, createdAt, maxPendingBytes, defaultResumePendingBytes(maxPendingBytes));
    }

    public DestinationQueueMetadata(
            String destination,
            Instant createdAt,
            long maxPendingBytes,
            long resumePendingBytes
    ) {
        if (destination.isBlank()) {
            throw new IllegalArgumentException("Destination must not be blank");
        }
        validateThresholds(maxPendingBytes, resumePendingBytes);
        this.destination = destination;
        this.createdAt = createdAt;
        this.maxPendingBytes = maxPendingBytes;
        this.resumePendingBytes = resumePendingBytes;
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

    public long getResumePendingBytes() {
        return resumePendingBytes;
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean isSaturated() {
        return getPendingBytes() >= maxPendingBytes;
    }

    public boolean canResume() {
        return getPendingBytes() <= resumePendingBytes;
    }

    static long defaultResumePendingBytes(long maxPendingBytes) {
        if (maxPendingBytes <= 0) {
            throw new IllegalArgumentException("Max pending bytes must be greater than zero");
        }
        return maxPendingBytes - Math.max(1, maxPendingBytes / 4);
    }

    static void validateThresholds(long maxPendingBytes, long resumePendingBytes) {
        if (maxPendingBytes <= 0) {
            throw new IllegalArgumentException("Max pending bytes must be greater than zero");
        }
        if (resumePendingBytes < 0 || resumePendingBytes >= maxPendingBytes) {
            throw new IllegalArgumentException(
                    "Resume pending bytes must be at least zero and lower than max pending bytes"
            );
        }
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
