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
package org.traffichunter.titan.core.resilience.flowcontrol;

import org.traffichunter.titan.core.util.management.HeapResourceDetector;
import org.traffichunter.titan.core.util.management.ResourceDetector;
import org.traffichunter.titan.core.util.management.HeapResource;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yun
 */
public final class MemoryPressureDamper implements Damper {

    private final ResourceDetector<HeapResource> resourceDetector;
    private final AtomicReference<DamperStatus> statusUpdater = new AtomicReference<>(DamperStatus.OPEN);
    private final double highWatermark;
    private final double lowWatermark;

    public MemoryPressureDamper(FlowControlConfiguration configuration) {
        this(new HeapResourceDetector(), configuration);
    }

    MemoryPressureDamper(
            ResourceDetector<HeapResource> resourceDetector,
            FlowControlConfiguration configuration
    ) {
        this.resourceDetector = resourceDetector;
        this.highWatermark = configuration.highWatermark();
        this.lowWatermark = configuration.lowWatermark();
    }

    @Override
    public DamperStatus regulate() {
        double memoryUsage = resourceDetector.detect().usage();

        return statusUpdater.updateAndGet(currentStatus -> {
            if (currentStatus == DamperStatus.OPEN && memoryUsage >= highWatermark) {
                return DamperStatus.CLOSED;
            }

            if (currentStatus == DamperStatus.CLOSED && memoryUsage <= lowWatermark) {
                return DamperStatus.OPEN;
            }

            return currentStatus;
        });
    }

    @Override
    public void open() {
        statusUpdater.set(DamperStatus.OPEN);
    }

    @Override
    public void close() {
        statusUpdater.set(DamperStatus.CLOSED);
    }

    @Override
    public DamperStatus getStatus() {
        return statusUpdater.get();
    }
}
