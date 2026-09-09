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
 * Detects a snapshot of a JVM or operating-system resource.
 *
 * <p>Implementations perform one measurement per invocation and do not own
 * scheduling or threshold policy. Callers that require periodic sampling are
 * responsible for scheduling calls to {@link #detect()}.</p>
 *
 * @param <T> resource snapshot type
 * @author yun
 */
public interface ResourceDetector<T> {

    /**
     * Measures and returns the current resource state.
     *
     * @return current resource snapshot
     */
    T detect();
}
