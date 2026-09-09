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
package org.traffichunter.titan.monitor.jmx.heap;

import org.traffichunter.titan.core.util.management.HeapResource;
import org.traffichunter.titan.core.util.management.HeapResourceDetector;
import org.traffichunter.titan.core.util.management.ResourceDetector;
import org.traffichunter.titan.monitor.jmx.JmxMbeanCollector;

/**
 * @author yungwang-o
 */
public final class JmxHeapMbeanCollector implements JmxMbeanCollector<HeapData> {

    private final ResourceDetector<HeapResource> resourceDetector;

    public JmxHeapMbeanCollector() {
        this(new HeapResourceDetector());
    }

    public JmxHeapMbeanCollector(ResourceDetector<HeapResource> resourceDetector) {
        this.resourceDetector = resourceDetector;
    }

    @Override
    public CollectorType getCollectorType() {
        return CollectorType.HEAP;
    }

    @Override
    public Class<HeapData> getDataType() {
        return HeapData.class;
    }

    @Override
    public HeapData collect() {
        HeapResource heap = resourceDetector.detect();
        return new HeapData(heap.init(), heap.used(), heap.committed(), heap.max());
    }
}
