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
package org.traffichunter.titan.core.util.management;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * Detects CPU usage through the platform operating-system management bean.
 *
 * @author yun
 */
public final class CpuResourceDetector implements ResourceDetector<CpuResource> {

    private final OperatingSystemMXBean operatingSystemMXBean;

    /**
     * Creates a detector backed by the platform operating-system bean.
     */
    public CpuResourceDetector() {
        this(ManagementFactory.getOperatingSystemMXBean());
    }

    /**
     * Creates a detector backed by the supplied operating-system bean.
     *
     * @param operatingSystemMXBean operating-system bean used for measurements
     */
    public CpuResourceDetector(OperatingSystemMXBean operatingSystemMXBean) {
        this.operatingSystemMXBean = operatingSystemMXBean;
    }

    @Override
    public CpuResource detect() {
        double systemCpuLoad = -1.0;
        double processCpuLoad = -1.0;
        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean extendedBean) {
            systemCpuLoad = extendedBean.getCpuLoad();
            processCpuLoad = extendedBean.getProcessCpuLoad();
        }

        return new CpuResource(
                systemCpuLoad,
                processCpuLoad,
                operatingSystemMXBean.getAvailableProcessors()
        );
    }
}
