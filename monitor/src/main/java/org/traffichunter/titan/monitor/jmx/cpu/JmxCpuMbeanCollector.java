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
package org.traffichunter.titan.monitor.jmx.cpu;

import org.traffichunter.titan.core.util.management.CpuResource;
import org.traffichunter.titan.core.util.management.CpuResourceDetector;
import org.traffichunter.titan.core.util.management.ResourceDetector;
import org.traffichunter.titan.monitor.jmx.JmxMbeanCollector;

/**
 * @author yungwang-o
 */
public final class JmxCpuMbeanCollector implements JmxMbeanCollector<CpuData> {

    private final ResourceDetector<CpuResource> resourceDetector;

    public JmxCpuMbeanCollector() {
        this(new CpuResourceDetector());
    }

    public JmxCpuMbeanCollector(ResourceDetector<CpuResource> resourceDetector) {
        this.resourceDetector = resourceDetector;
    }

    @Override
    public CollectorType getCollectorType() {
        return CollectorType.CPU;
    }

    @Override
    public Class<CpuData> getDataType() {
        return CpuData.class;
    }

    @Override
    public CpuData collect() {
        CpuResource cpu = resourceDetector.detect();
        return new CpuData(cpu.systemCpuLoad(), cpu.processCpuLoad(), cpu.availableProcessors());
    }
}
