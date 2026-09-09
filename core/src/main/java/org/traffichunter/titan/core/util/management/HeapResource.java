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

/**
 * Immutable snapshot of JVM heap memory usage, expressed in bytes.
 *
 * <p>Some JVMs report an undefined maximum value. In that case,
 * {@link #limit()} falls back to committed memory so callers can still derive
 * a usable pressure ratio.</p>
 *
 * @param init initial heap size, or a negative value when undefined
 * @param used currently used heap size
 * @param committed heap size guaranteed to be available to the JVM
 * @param max maximum heap size, or a negative value when undefined
 * @author yun
 */
public record HeapResource(long init, long used, long committed, long max) {

    /**
     * Returns the effective upper bound used for pressure calculations.
     *
     * @return maximum heap size when defined, otherwise committed heap size
     */
    public long limit() {
        return max > 0 ? max : committed;
    }

    /**
     * Returns current heap usage as a value between {@code 0.0} and
     * {@code 1.0}. Invalid or unavailable limits produce {@code 0.0}.
     *
     * @return normalized heap usage
     */
    public double usage() {
        long limit = limit();
        if (limit <= 0 || used <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) used / limit);
    }
}
