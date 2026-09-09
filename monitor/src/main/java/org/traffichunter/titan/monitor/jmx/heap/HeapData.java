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

import org.traffichunter.titan.monitor.jmx.ThreshHold;

/**
 * @author yungwang-o
 */
public record HeapData(

        long init,

        long used,

        long committed,

        long max

) implements ThreshHold {

    public static HeapDataBuilder builder() {
        return new HeapDataBuilder();
    }

    @Override
    public boolean isCheckThreshold(final double factor) {
        if (this.max() <= 0) {
            return false;
        }

        double usageRate = (double) this.used() / this.max();
        return usageRate > factor;
    }

    public static final class HeapDataBuilder {

        private long init;
        private long used;
        private long committed;
        private long max;

        private HeapDataBuilder() {
        }

        public HeapDataBuilder init(long value) {
            this.init = value;
            return this;
        }

        public HeapDataBuilder used(long value) {
            this.used = value;
            return this;
        }

        public HeapDataBuilder committed(long value) {
            this.committed = value;
            return this;
        }

        public HeapDataBuilder max(long value) {
            this.max = value;
            return this;
        }

        public HeapData build() {
            return new HeapData(init, used, committed, max);
        }
    }
}
