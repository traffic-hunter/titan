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
package org.traffichunter.titan.monitor.jmx.thread;

import org.traffichunter.titan.monitor.jmx.ThreshHold;

/**
 * @author yungwang-o
 */
public record ThreadData(

        int threadCount,

        int peakThreadCount,

        long totalStartedThreadCount

) implements ThreshHold {

    public static ThreadDataBuilder builder() {
        return new ThreadDataBuilder();
    }

    @Override
    public boolean isCheckThreshold(final double factor) {
        return this.threadCount() > (int) factor;
    }

    public static final class ThreadDataBuilder {

        private int threadCount;
        private int peakThreadCount;
        private long totalStartedThreadCount;

        private ThreadDataBuilder() {
        }

        public ThreadDataBuilder threadCount(int value) {
            this.threadCount = value;
            return this;
        }

        public ThreadDataBuilder peakThreadCount(int value) {
            this.peakThreadCount = value;
            return this;
        }

        public ThreadDataBuilder totalStartedThreadCount(long value) {
            this.totalStartedThreadCount = value;
            return this;
        }

        public ThreadData build() {
            return new ThreadData(threadCount, peakThreadCount, totalStartedThreadCount);
        }
    }
}
