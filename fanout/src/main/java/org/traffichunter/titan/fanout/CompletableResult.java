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
import java.util.concurrent.atomic.AtomicInteger;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.Destination;

/**
 * @author yun
 */
public final class CompletableResult {

    private final Collection<Destination> destinations;
    private final int attempted;
    private final Promise<CompletableResult> promise;
    private final AtomicInteger done;
    private final AtomicInteger succeeded;
    private final AtomicInteger failed;

    private CompletableResult(
            Collection<Destination> destinations,
            int attempted,
            Promise<CompletableResult> promise,
            int done,
            int succeeded,
            int failed
    ) {
        this.destinations = destinations;
        this.attempted = attempted;
        this.promise = promise;
        this.done = new AtomicInteger(done);
        this.succeeded = new AtomicInteger(succeeded);
        this.failed = new AtomicInteger(failed);
    }

    public static CompletableResult pending(
            Collection<Destination> destinations,
            int attempted,
            Promise<CompletableResult> promise
    ) {
        CompletableResult result = new CompletableResult(destinations, attempted, promise, 0, 0, 0);
        if (attempted == 0) {
            promise.trySuccess(result);
        }
        return result;
    }

    public static CompletableResult completed(
            Collection<Destination> destinations,
            int attempted,
            int succeeded,
            int failed,
            Promise<CompletableResult> promise
    ) {
        CompletableResult result = new CompletableResult(
                destinations,
                attempted,
                promise,
                attempted,
                succeeded,
                failed
        );
        promise.trySuccess(result);
        return result;
    }

    public void markSuccess() {
        succeeded.incrementAndGet();
        completeIfDone();
    }

    public void markFailure() {
        failed.incrementAndGet();
        completeIfDone();
    }

    public Collection<Destination> destinations() {
        return destinations;
    }

    public int attempted() {
        return attempted;
    }

    public int done() {
        return done.get();
    }

    public int succeeded() {
        return succeeded.get();
    }

    public int failed() {
        return failed.get();
    }

    public boolean isSuccess() {
        return done() == attempted && failed() == 0;
    }

    public Promise<CompletableResult> promise() {
        return promise;
    }

    private void completeIfDone() {
        if (done.incrementAndGet() == attempted) {
            promise.trySuccess(this);
        }
    }
}
