/*
 * Copyright 2024 traffic-hunter
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
