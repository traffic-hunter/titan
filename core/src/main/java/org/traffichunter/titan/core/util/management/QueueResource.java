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
 * Immutable snapshot of a dispatcher queue exposed through JMX.
 *
 * @param destination queue destination
 * @param size current number of queued messages
 * @param pendingBytes current queued payload bytes
 * @param maxPendingBytes maximum queued payload bytes
 * @param resumePendingBytes queued payload bytes at which admission resumes
 * @param paused whether the queue currently rejects or delays new work
 * @author yun
 */
public record QueueResource(
        String destination,
        int size,
        long pendingBytes,
        long maxPendingBytes,
        long resumePendingBytes,
        boolean paused
) {
}
