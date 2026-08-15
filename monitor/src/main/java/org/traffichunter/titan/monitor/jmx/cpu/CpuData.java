/*
 * The MIT License
 *
 * Copyright (c) 2024 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
