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
package org.traffichunter.titan.perftest;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Per-identifier record of what happened to every requested message.
 *
 * <p>Each stage is stored on its own. A MESSAGE can be observed before the RECEIPT that accepted
 * it, so reception is never forced to follow acceptance in a single ordered state.</p>
 *
 * <p>Producer threads and the consumer's I/O threads both write here, so every update takes the
 * shared side of {@link #freezeLock} and settles one identifier with an atomic transition. No
 * network work happens under either side of the lock. {@link #freeze()} takes the exclusive side:
 * once it returns, the numbers behind the snapshot can no longer move, and a late MESSAGE or a
 * send that completes afterwards is counted separately and left out of the report.</p>
 *
 * @author yun
 */
final class MeasurementLedger {

    /** What a producer learned about one attempt. Exactly one of these settles an identifier. */
    enum Outcome {
        /** No send call was made for this identifier. */
        NOT_ATTEMPTED,
        /** The local write was submitted. Says nothing about the broker. */
        WRITE_SUBMITTED,
        /** The broker answered with a RECEIPT for this identifier. */
        ACCEPTED,
        /** The broker answered that it did not take the message. */
        REJECTED,
        /** The client refused before any byte reached the transport. */
        LOCAL_NOT_SENT,
        /** Neither acceptance nor a confirmed non-delivery. */
        UNKNOWN
    }

    /** What one observed MESSAGE was worth. */
    enum Reception {
        /** First sighting of this identifier. */
        FIRST,
        /** The identifier had already been received. */
        DUPLICATE,
        /** The report was already frozen, or the identifier is out of range. */
        IGNORED
    }

    private static final Outcome[] OUTCOMES = Outcome.values();

    private final int requested;
    private final int warmupRequested;

    private final AtomicIntegerArray outcomes;
    private final AtomicIntegerArray receptions;
    private final AtomicIntegerArray warmupReceptions;
    private final long[] deliveryLatencyNanos;
    private final long[] acceptLatencyNanos;

    private final AtomicInteger foreignMessages = new AtomicInteger();
    private final AtomicInteger malformedMessages = new AtomicInteger();
    private final AtomicInteger settledTwice = new AtomicInteger();
    private final AtomicInteger lateEvents = new AtomicInteger();

    private final ReadWriteLock freezeLock = new ReentrantReadWriteLock();
    private volatile boolean frozen;

    MeasurementLedger(int requested, int warmupRequested) {
        this.requested = requested;
        this.warmupRequested = warmupRequested;
        this.outcomes = new AtomicIntegerArray(requested);
        this.receptions = new AtomicIntegerArray(requested);
        this.warmupReceptions = new AtomicIntegerArray(Math.max(warmupRequested, 1));
        this.deliveryLatencyNanos = new long[requested];
        this.acceptLatencyNanos = new long[requested];
    }

    /** Records that the local write was submitted, the strongest claim the write mode can make. */
    void writeSubmitted(int id, long latencyNanos) {
        settle(id, Outcome.WRITE_SUBMITTED, latencyNanos);
    }

    /** Records the broker's RECEIPT for this identifier along with how long it took. */
    void accepted(int id, long latencyNanos) {
        settle(id, Outcome.ACCEPTED, latencyNanos);
    }

    /** Records that the broker said it did not take this message. */
    void rejected(int id) {
        settle(id, Outcome.REJECTED, -1);
    }

    /** Records that the client refused the send before it reached the transport. */
    void localNotSent(int id) {
        settle(id, Outcome.LOCAL_NOT_SENT, -1);
    }

    /** Records an attempt whose fate the runner cannot determine. */
    void unknown(int id) {
        settle(id, Outcome.UNKNOWN, -1);
    }

    /** Records one observed measurement MESSAGE. */
    Reception received(int id, long latencyNanos) {
        if (id < 0 || id >= requested) {
            malformedMessages.incrementAndGet();
            return Reception.IGNORED;
        }

        freezeLock.readLock().lock();
        try {
            if (frozen) {
                lateEvents.incrementAndGet();
                return Reception.IGNORED;
            }
            if (receptions.incrementAndGet(id) != 1) {
                return Reception.DUPLICATE;
            }
            deliveryLatencyNanos[id] = latencyNanos;
            return Reception.FIRST;
        } finally {
            freezeLock.readLock().unlock();
        }
    }

    /** Records one observed warm-up MESSAGE. Warm-up traffic never enters the measurement counts. */
    Reception warmupReceived(int index) {
        if (index < 0 || index >= warmupRequested) {
            malformedMessages.incrementAndGet();
            return Reception.IGNORED;
        }

        freezeLock.readLock().lock();
        try {
            if (frozen) {
                lateEvents.incrementAndGet();
                return Reception.IGNORED;
            }
            return warmupReceptions.incrementAndGet(index) == 1 ? Reception.FIRST : Reception.DUPLICATE;
        } finally {
            freezeLock.readLock().unlock();
        }
    }

    /** Counts a MESSAGE stamped with another run's identifier. */
    void foreignMessage() {
        foreignMessages.incrementAndGet();
    }

    /** Counts a MESSAGE whose measurement header could not be read. */
    void malformedMessage() {
        malformedMessages.incrementAndGet();
    }

    /** Returns how many events arrived after the report was frozen. */
    int lateEvents() {
        return lateEvents.get();
    }

    /**
     * Fixes the report. Every later event is counted as late and changes nothing here.
     *
     * @return the immutable counts and latency samples of this run
     */
    MeasurementSnapshot freeze() {
        freezeLock.writeLock().lock();
        try {
            frozen = true;
            return collect();
        } finally {
            freezeLock.writeLock().unlock();
        }
    }

    private void settle(int id, Outcome outcome, long latencyNanos) {
        if (id < 0 || id >= requested) {
            throw new IllegalArgumentException("Identifier outside the requested range: " + id);
        }

        freezeLock.readLock().lock();
        try {
            if (frozen) {
                lateEvents.incrementAndGet();
                return;
            }
            if (!outcomes.compareAndSet(id, Outcome.NOT_ATTEMPTED.ordinal(), outcome.ordinal())) {
                settledTwice.incrementAndGet();
                return;
            }
            if (latencyNanos >= 0) {
                acceptLatencyNanos[id] = latencyNanos;
            }
        } finally {
            freezeLock.readLock().unlock();
        }
    }

    private MeasurementSnapshot collect() {
        int notAttempted = 0;
        int writeSubmitted = 0;
        int accepted = 0;
        int rejected = 0;
        int localNotSent = 0;
        int unknown = 0;
        int received = 0;
        int duplicates = 0;
        int acceptedNotReceived = 0;
        int contradiction = settledTwice.get();
        int latencySamples = 0;
        int acceptSamples = 0;

        long[] delivery = new long[requested];
        long[] accept = new long[requested];

        for (int id = 0; id < requested; id++) {
            Outcome outcome = OUTCOMES[outcomes.get(id)];
            int seen = receptions.get(id);
            switch (outcome) {
                case NOT_ATTEMPTED -> notAttempted++;
                case WRITE_SUBMITTED -> writeSubmitted++;
                case ACCEPTED -> accepted++;
                case REJECTED -> rejected++;
                case LOCAL_NOT_SENT -> localNotSent++;
                case UNKNOWN -> unknown++;
            }
            if (seen > 0) {
                received++;
                duplicates += seen - 1;
                delivery[latencySamples++] = deliveryLatencyNanos[id];
                // The broker said this identifier never left the runner, yet it came back.
                if (outcome == Outcome.REJECTED || outcome == Outcome.LOCAL_NOT_SENT) {
                    contradiction++;
                }
            } else if (outcome == Outcome.ACCEPTED) {
                acceptedNotReceived++;
            }
            if (outcome == Outcome.ACCEPTED || outcome == Outcome.WRITE_SUBMITTED) {
                accept[acceptSamples++] = acceptLatencyNanos[id];
            }
        }

        int warmupReceived = 0;
        for (int index = 0; index < warmupRequested; index++) {
            if (warmupReceptions.get(index) > 0) {
                warmupReceived++;
            }
        }

        return new MeasurementSnapshot(
                requested,
                requested - notAttempted,
                notAttempted,
                writeSubmitted,
                accepted,
                rejected,
                localNotSent,
                unknown,
                received,
                duplicates,
                acceptedNotReceived,
                contradiction,
                warmupRequested,
                warmupReceived,
                foreignMessages.get(),
                malformedMessages.get(),
                sorted(delivery, latencySamples),
                sorted(accept, acceptSamples)
        );
    }

    private static long[] sorted(long[] values, int length) {
        long[] samples = new long[length];
        System.arraycopy(values, 0, samples, 0, length);
        Arrays.sort(samples);
        return samples;
    }
}
