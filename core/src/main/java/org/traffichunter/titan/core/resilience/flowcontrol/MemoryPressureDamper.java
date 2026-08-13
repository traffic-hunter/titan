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
