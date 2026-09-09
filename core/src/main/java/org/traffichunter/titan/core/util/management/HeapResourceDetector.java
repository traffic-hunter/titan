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
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Detects JVM heap usage through the platform {@link MemoryMXBean}.
 *
 * @author yun
 */
public final class HeapResourceDetector implements ResourceDetector<HeapResource> {

    private final MemoryMXBean memoryMXBean;

    /**
     * Creates a detector backed by the platform memory management bean.
     */
    public HeapResourceDetector() {
        this(ManagementFactory.getMemoryMXBean());
    }

    /**
     * Creates a detector backed by the supplied memory management bean.
     *
     * @param memoryMXBean memory management bean used for measurements
     */
    public HeapResourceDetector(MemoryMXBean memoryMXBean) {
        this.memoryMXBean = memoryMXBean;
    }

    @Override
    public HeapResource detect() {
        MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
        return new HeapResource(
                usage.getInit(),
                usage.getUsed(),
                usage.getCommitted(),
                usage.getMax()
        );
    }
}
