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
package org.traffichunter.titan.fanout;

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
