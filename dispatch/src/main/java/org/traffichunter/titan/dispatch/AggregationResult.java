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

import java.util.Collection;
import org.traffichunter.titan.core.util.Destination;

/**
 * Aggregates completion counts for a single export attempt.
 *
 * <p>Fanout delivery can target zero, one, or many consumers. This object keeps
 * the destination set, the number of attempted writes, and the success/failure
 * counters in one place.</p>
 */
public final class AggregationResult {

    private final Collection<Destination> destinations;
    private final int totalAttempted;
    private int done;
    private int succeeded;
    private int failed;

    private AggregationResult(
            Collection<Destination> destinations,
            int totalAttempted,
            int done,
            int succeeded,
            int failed
    ) {
        this.destinations = destinations;
        this.totalAttempted = totalAttempted;
        this.done = done;
        this.succeeded = succeeded;
        this.failed = failed;
    }

    public static AggregationResult create(Collection<Destination> destinations, int attempted) {
        return new AggregationResult(
                destinations,
                attempted,
                0,
                0,
                0
        );
    }

    public static AggregationResult completed(
            Collection<Destination> destinations,
            int attempted,
            int succeeded,
            int failed
    ) {
        return new AggregationResult(
                destinations,
                attempted,
                attempted,
                succeeded,
                failed
        );
    }

    public void success() {
        succeeded++;
        incrementDone();
    }

    public void fail() {
        failed++;
        incrementDone();
    }

    public Collection<Destination> destinations() {
        return destinations;
    }

    public int totalAttempted() {
        return totalAttempted;
    }

    public int done() {
        return done;
    }

    public int succeeded() {
        return succeeded;
    }

    public int failed() {
        return failed;
    }

    public boolean isSuccess() {
        return isDone() && failed() == 0;
    }

    public boolean isDone() {
        return done() >= totalAttempted;
    }

    private void incrementDone() {
        done++;
    }
}
