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
 * Immutable snapshot of system and current-process CPU usage.
 *
 * <p>Load values are normalized between {@code 0.0} and {@code 1.0}. A
 * negative load indicates that the running JVM does not expose that metric.</p>
 *
 * @param systemCpuLoad CPU load for the whole system
 * @param processCpuLoad CPU load for the current JVM process
 * @param availableProcessors processors available to the JVM
 * @author yun
 */
public record CpuResource(
        double systemCpuLoad,
        double processCpuLoad,
        int availableProcessors
) {
}
