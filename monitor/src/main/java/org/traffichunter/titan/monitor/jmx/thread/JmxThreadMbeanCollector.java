/*
 * The MIT License
 *
 * Copyright (c) 2024 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.monitor.jmx.thread;

import org.traffichunter.titan.core.util.management.ResourceDetector;
import org.traffichunter.titan.core.util.management.ThreadResource;
import org.traffichunter.titan.core.util.management.ThreadResourceDetector;
import org.traffichunter.titan.monitor.jmx.JmxMbeanCollector;

/**
 * @author yungwang-o
 */
public final class JmxThreadMbeanCollector implements JmxMbeanCollector<ThreadData> {

    private final ResourceDetector<ThreadResource> resourceDetector;

    public JmxThreadMbeanCollector() {
        this(new ThreadResourceDetector());
    }

    public JmxThreadMbeanCollector(ResourceDetector<ThreadResource> resourceDetector) {
        this.resourceDetector = resourceDetector;
    }

    @Override
    public CollectorType getCollectorType() {
        return CollectorType.THREAD;
    }

    @Override
    public Class<ThreadData> getDataType() {
        return ThreadData.class;
    }

    @Override
    public ThreadData collect() {
        ThreadResource thread = resourceDetector.detect();
        return new ThreadData(
                thread.threadCount(),
                thread.peakThreadCount(),
                thread.totalStartedThreadCount()
        );
    }
}
