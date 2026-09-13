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

/**
 * The fixed counts and latency samples of one run.
 *
 * <p>Reception is counted on its own and is never added into the attempt total: a message can be
 * received while its own attempt is still unsettled.</p>
 *
 * @param requested messages the run planned to send
 * @param attempted identifiers a send call was made for
 * @param notAttempted identifiers no send call was made for
 * @param writeSubmitted local writes submitted, counted only in the write send mode
 * @param accepted identifiers the broker answered with a RECEIPT, counted only in the receipt mode
 * @param rejected identifiers the broker explicitly refused
 * @param localNotSent identifiers the client refused before reaching the transport
 * @param unknown attempts with neither acceptance nor a confirmed non-delivery
 * @param received distinct identifiers observed on the consumer
 * @param duplicates extra sightings beyond the first for an identifier
 * @param acceptedNotReceived accepted identifiers never observed before the deadline
 * @param contradiction sightings that break the contract, such as a refused identifier arriving
 * @param warmupRequested warm-up messages the run planned to send
 * @param warmupReceived distinct warm-up messages observed
 * @param foreignMessages messages stamped with another run's identifier
 * @param malformedMessages messages whose measurement header could not be read
 * @param deliveryLatencyNanos sorted end-to-end samples, one per distinct reception
 * @param acceptLatencyNanos sorted samples of how long a settled send took
 *
 * @author yun
 */
record MeasurementSnapshot(
        int requested,
        int attempted,
        int notAttempted,
        int writeSubmitted,
        int accepted,
        int rejected,
        int localNotSent,
        int unknown,
        int received,
        int duplicates,
        int acceptedNotReceived,
        int contradiction,
        int warmupRequested,
        int warmupReceived,
        int foreignMessages,
        int malformedMessages,
        long[] deliveryLatencyNanos,
        long[] acceptLatencyNanos
) {

    /**
     * Returns whether the attempt totals add up.
     *
     * <p>A false value is a defect in the runner's own accounting rather than a broker result, so
     * it is reported instead of being repaired.</p>
     *
     * @return {@code true} when every requested identifier is counted exactly once
     */
    boolean countsBalanced() {
        return requested == attempted + notAttempted
                && attempted == writeSubmitted + accepted + rejected + localNotSent + unknown;
    }
}
