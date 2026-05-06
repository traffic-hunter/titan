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

import org.traffichunter.titan.core.message.dispatcher.Dispatcher;
import org.traffichunter.titan.core.util.concurrent.Damper;
import org.traffichunter.titan.fanout.exporter.FanoutExporter;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

/**
 * Fanout gateway backed by one virtual thread per submitted task.
 *
 * <p>Destination consumers are naturally long-lived and often block while
 * waiting for dispatcher queues. Virtual threads make that blocking cheap, while
 * the internal damper still caps active dispatch sections so exporter work does
 * not grow without a limit.</p>
 */
class VirtualThreadExecutorFanoutGateway extends AbstractExecutorFanoutGateway {

    private static final int DEFAULT_CONCURRENCY_LIMIT = 100;

    public VirtualThreadExecutorFanoutGateway(FanoutExporter exporter) {
        this(exporter, Dispatcher.getDefault());
    }

    public VirtualThreadExecutorFanoutGateway(FanoutExporter exporter, Dispatcher dispatcher) {
        super(
                Executors.newThreadPerTaskExecutor(newThreadFactory()),
                exporter,
                dispatcher,
                new SemaphoreBasedFlowControlDamper(DEFAULT_CONCURRENCY_LIMIT)
        );
    }

    private static ThreadFactory newThreadFactory() {
        return Thread.ofVirtual()
                .name("FanoutVirtualThread")
                .factory();
    }

    private static final class SemaphoreBasedFlowControlDamper implements Damper {

        private final Semaphore semaphore;

        private SemaphoreBasedFlowControlDamper(int limit) {
            this.semaphore = new Semaphore(limit);
        }

        @Override
        public void acquire() {
            semaphore.acquireUninterruptibly();
        }

        @Override
        public void release() {
            semaphore.release();
        }
    }
}
