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

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.DispatchResult;
import org.traffichunter.titan.core.util.Destination;

/**
 * {@link DispatcherQueueManager} over the queues of one {@link PathFinder}.
 *
 * <p>It only needs the path finder. The consumer of a queue starts and stops through the
 * path finder's {@link DispatcherQueueListener}, so creating or deleting a queue here is enough.</p>
 *
 * @author yun
 */
final class PathFinderQueueManager implements DispatcherQueueManager {

    private final PathFinder pathFinder;

    PathFinderQueueManager(PathFinder pathFinder) {
        this.pathFinder = pathFinder;
    }

    /**
     * Creates a dispatcher queue through the path finder.
     *
     * <p>Queue creation is idempotent. If the queue already exists, the
     * existing instance is returned and the supplied byte limit is ignored.</p>
     */
    @Override
    public DispatcherQueue createQueue(String group, Destination destination, long maxPendingBytes) {
        return pathFinder.getOrPut(group, destination, maxPendingBytes);
    }

    /**
     * Deletes a dispatcher queue. Its consumer stops when the queue leaves the path finder.
     *
     * <p>The queue is closed before it leaves the path finder, so a producer admitted in the
     * meantime is refused outright rather than filling a queue this call is about to drop.
     * Non-empty queues are rejected unless force deletion is requested.</p>
     */
    @Override
    public DispatcherQueueDeleteResult deleteQueue(String group, Destination destination, boolean force) {
        DispatcherQueue queue = getQueue(group, destination);
        if (queue == null) {
            return DispatcherQueueDeleteResult.notFound();
        }

        int size = queue.size();
        if (size > 0 && !force) {
            return DispatcherQueueDeleteResult.notEmpty(size);
        }

        queue.close();

        // Remove the queue this call looked up, never a replacement created since. The
        // path finder unregisters the MBean of whatever it actually removed.
        if (!pathFinder.remove(queue)) {
            return DispatcherQueueDeleteResult.notFound();
        }
        if (force) {
            queue.clear();
        }
        return DispatcherQueueDeleteResult.deleted(size);
    }

    /**
     * Manually pauses the queue without detaching its consumer.
     *
     * <p>A manual pause immediately refuses new messages with {@link DispatchResult#PAUSED}
     * and stops delivering queued messages. The consumer
     * stays attached and waits until the queue is resumed. To discard the
     * queued messages instead, use {@link #purgeQueue(String, Destination)}.</p>
     */
    @Override
    public boolean pauseQueue(String group, Destination destination) {
        DispatcherQueue queue = getQueue(group, destination);
        if (queue == null) {
            return false;
        }
        queue.pause();
        return true;
    }

    /**
     * Clears the manual pause so the queue admits and delivers messages again.
     *
     * <p>A queue that is still over its byte limit stays paused for producers
     * by flow control, because the queue re-evaluates its own pause state
     * rather than trusting the caller. A pressure pause never blocks dispatch,
     * so consumers can drain the queue back below the resume threshold.</p>
     */
    @Override
    public boolean resumeQueue(String group, Destination destination) {
        DispatcherQueue queue = getQueue(group, destination);
        if (queue == null) {
            return false;
        }
        queue.resume();
        return true;
    }

    /**
     * Drops every pending message while keeping the queue and its consumer.
     */
    @Override
    public boolean purgeQueue(String group, Destination destination) {
        DispatcherQueue queue = getQueue(group, destination);
        if (queue == null) {
            return false;
        }
        queue.clear();
        return true;
    }

    /** Looks the queue up inside its own group. An unknown group is not created. */
    private @Nullable DispatcherQueue getQueue(String group, Destination destination) {
        return pathFinder.get(group, destination);
    }
}
