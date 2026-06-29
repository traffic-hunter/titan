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
import org.traffichunter.titan.core.util.concurrent.NoopDamper;
import org.traffichunter.titan.fanout.exporter.DispatchExporter;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Dispatch gateway backed by a fixed-size platform thread pool.
 *
 * <p>This mode is useful when dispatch work should be bounded by a small number
 * of OS threads. It limits executor parallelism directly through the pool size.</p>
 *
 * @author yun
 */
class ThreadPoolExecutorDispatchGateway extends AbstractExecutorDispatchGateway {

    public ThreadPoolExecutorDispatchGateway(DispatchExporter exporter) {
        this(exporter, Dispatcher.getDefault());
    }

    public ThreadPoolExecutorDispatchGateway(DispatchExporter exporter, Dispatcher dispatcher) {
        this(Runtime.getRuntime().availableProcessors() * 2, exporter, dispatcher);
    }

    public ThreadPoolExecutorDispatchGateway(
            int nThreads,
            DispatchExporter exporter,
            Dispatcher dispatcher
    ) {
        super(
                Executors.newFixedThreadPool(nThreads, newThreadFactory()),
                exporter,
                dispatcher,
                NoopDamper.getInstance()
        );
    }

    private static ThreadFactory newThreadFactory() {
        return Thread.ofPlatform()
                .name("FanoutThread")
                .factory();
    }
}
