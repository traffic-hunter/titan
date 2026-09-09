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

import com.sun.management.OperatingSystemMXBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class CpuResourceDetectorTest {

    @Test
    void detect_cpu_resource_from_operating_system_management_bean() {
        OperatingSystemMXBean operatingSystemMXBean = mock(OperatingSystemMXBean.class);
        when(operatingSystemMXBean.getCpuLoad()).thenReturn(0.7);
        when(operatingSystemMXBean.getProcessCpuLoad()).thenReturn(0.3);
        when(operatingSystemMXBean.getAvailableProcessors()).thenReturn(8);

        CpuResource cpu = new CpuResourceDetector(operatingSystemMXBean).detect();

        assertThat(cpu).isEqualTo(new CpuResource(0.7, 0.3, 8));
    }

    @Test
    void report_unavailable_cpu_load_for_standard_management_bean() {
        java.lang.management.OperatingSystemMXBean operatingSystemMXBean =
                mock(java.lang.management.OperatingSystemMXBean.class);
        when(operatingSystemMXBean.getAvailableProcessors()).thenReturn(4);

        CpuResource cpu = new CpuResourceDetector(operatingSystemMXBean).detect();

        assertThat(cpu).isEqualTo(new CpuResource(-1.0, -1.0, 4));
    }
}
