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
