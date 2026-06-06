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

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import org.traffichunter.titan.monitor.jmx.JmxMbeanCollector;

/**
 * @author yungwang-o
 */
public final class JmxThreadMbeanCollector implements JmxMbeanCollector<ThreadData> {

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

        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

        return ThreadData.builder()
                .threadCount(threadMXBean.getThreadCount())
                .getPeekThreadCount(threadMXBean.getPeakThreadCount())
                .getTotalStartThreadCount(threadMXBean.getTotalStartedThreadCount())
                .build();
    }
}
