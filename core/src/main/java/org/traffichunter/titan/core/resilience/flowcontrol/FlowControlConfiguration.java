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

/**
 * @author yun
 */
public record FlowControlConfiguration(
        double highWatermark,
        double lowWatermark
) {

    public FlowControlConfiguration {
        if (highWatermark <= 0.0 || highWatermark > 1.0) {
            throw new IllegalArgumentException("High watermark must be greater than 0 and at most 1");
        }
        if (lowWatermark < 0.0 || lowWatermark >= highWatermark) {
            throw new IllegalArgumentException("Low watermark must be at least 0 and lower than high watermark");
        }
    }
}
