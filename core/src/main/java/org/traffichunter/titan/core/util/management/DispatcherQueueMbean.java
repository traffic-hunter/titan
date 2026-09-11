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

import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * @author yungwang-o
 */
public interface DispatcherQueueMbean {

    /** Group used for queues created without an explicit group. */
    String DEFAULT_GROUP = DestinationGroups.DEFAULT;

    /** Group that owns the queue. Queues that predate groups report the default group. */
    default String getGroup() {
        return DEFAULT_GROUP;
    }

    String getDestination();

    int getSize();

    long getPendingBytes();

    long getMaxPendingBytes();

    long getResumePendingBytes();

    boolean isPaused();
}
