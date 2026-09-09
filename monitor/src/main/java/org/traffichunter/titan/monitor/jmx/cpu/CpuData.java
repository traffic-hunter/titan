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

import org.traffichunter.titan.monitor.jmx.ThreshHold;

/**
 * @author yungwang-o
 */
public record CpuData(

        double systemCpuLoad,

        double processCpuLoad,

        long availableProcessors

) implements ThreshHold {

    public static CpuDataBuilder builder() {
        return new CpuDataBuilder();
    }

    @Override
    public boolean isCheckThreshold(final double factor) {
        return this.systemCpuLoad() > factor;
    }

    public static final class CpuDataBuilder {

        private double systemCpuLoad;
        private double processCpuLoad;
        private long availableProcessors;

        private CpuDataBuilder() {
        }

        public CpuDataBuilder systemCpuLoad(double value) {
            this.systemCpuLoad = value;
            return this;
        }

        public CpuDataBuilder processCpuLoad(double value) {
            this.processCpuLoad = value;
            return this;
        }

        public CpuDataBuilder availableProcessors(long value) {
            this.availableProcessors = value;
            return this;
        }

        public CpuData build() {
            return new CpuData(systemCpuLoad, processCpuLoad, availableProcessors);
        }
    }
}
