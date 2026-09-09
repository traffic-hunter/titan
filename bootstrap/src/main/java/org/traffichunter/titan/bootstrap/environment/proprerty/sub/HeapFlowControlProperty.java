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
package org.traffichunter.titan.bootstrap.environment.proprerty.sub;

/** Mutable YAML DTO for heap-pressure hysteresis thresholds. */
public final class HeapFlowControlProperty {

    private boolean enabled = true;
    private double highWatermark = 0.90;
    private double lowWatermark = 0.70;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getHighWatermark() {
        return highWatermark;
    }

    public void setHighWatermark(double highWatermark) {
        this.highWatermark = highWatermark;
    }

    public double getLowWatermark() {
        return lowWatermark;
    }

    public void setLowWatermark(double lowWatermark) {
        this.lowWatermark = lowWatermark;
    }
}
