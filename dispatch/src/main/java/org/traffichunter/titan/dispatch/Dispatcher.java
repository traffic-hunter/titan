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
package org.traffichunter.titan.dispatch;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;

/**
 * Registry of destination queues.
 *
 * <p>Dispatchers do not deliver messages directly. They resolve the queue for a destination,
 * creating it when necessary, and queue consumers perform the actual dispatch from
 * {@link DispatcherQueue}.</p>
 *
 * @author yungwang-o
 */
public interface Dispatcher {

    /**
     * Returns Titan's default destination registry implementation.
     */
    static Dispatcher getDefault() {
        return new TrieDispatcher();
    }

    /** Returns the default destination registry with an automatic queue byte limit. */
    static Dispatcher getDefault(long maxPendingBytes) {
        return new TrieDispatcher(maxPendingBytes);
    }

    /** Returns the default destination registry with byte pause and resume thresholds. */
    static Dispatcher getDefault(long maxPendingBytes, long resumePendingBytes) {
        return new TrieDispatcher(maxPendingBytes, resumePendingBytes);
    }

    /**
     * Returns the queue for the destination, or {@code null} when it has not been created.
     */
    @Nullable DispatcherQueue get(Destination destination);

    /**
     * Returns the existing queue or creates one for this destination.
     */
    @CanIgnoreReturnValue
    DispatcherQueue getOrPut(Destination destination);

    /**
     * Returns the existing queue or creates one with the requested byte limit.
     *
     * <p>If the queue already exists, implementations should return it without
     * changing its byte limit.</p>
     */
    @CanIgnoreReturnValue
    DispatcherQueue getOrPut(Destination destination, long maxPendingBytes);

    /**
     * Returns queues matching the destination pattern.
     *
     * <p>Exact destinations return at most one queue. Wildcard destinations such as
     * {@code /queue/orders/*} return descendant queues under that prefix.</p>
     */
    List<DispatcherQueue> searchAll(Destination destination);

    boolean exists(Destination destination);

    void remove(Destination destination);
}
