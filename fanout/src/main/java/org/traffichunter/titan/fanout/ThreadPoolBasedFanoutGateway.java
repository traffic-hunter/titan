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
import org.traffichunter.titan.fanout.exporter.FanoutExporter;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author yun
 */
class ThreadPoolBasedFanoutGateway extends AbstractExecutorFanoutGateway {

    public ThreadPoolBasedFanoutGateway(FanoutExporter exporter) {
        this(exporter, Dispatcher.getDefault());
    }

    public ThreadPoolBasedFanoutGateway(FanoutExporter exporter, Dispatcher dispatcher) {
        this(Runtime.getRuntime().availableProcessors() * 2, exporter, dispatcher);
    }

    public ThreadPoolBasedFanoutGateway(int nThreads, FanoutExporter exporter, Dispatcher dispatcher) {
        super(Executors.newFixedThreadPool(nThreads, newThreadFactory()), exporter, dispatcher);
    }

    private static ThreadFactory newThreadFactory() {
        return Thread.ofPlatform()
                .name("FanoutThread")
                .factory();
    }
}
