package org.traffichunter.titan.dispatch;

import org.traffichunter.titan.core.util.Destination;

/**
 * Creates and deletes dispatcher queues owned by a running transport or
 * fanout component.
 *
 * <p>This interface provides a smaller set of operations than {@link Dispatcher}.
 * Management tools such as the monitor HTTP API use it to create or remove queues
 * without depending on a specific fanout implementation.</p>
 *
 * @author yungwang-o
 */
public interface DispatcherQueueManager {

    /**
     * Creates the queue for the destination if it does not exist.
     *
     * <p>Implementations should be idempotent. When the queue already exists,
     * they should return the existing queue and leave its original byte limit
     * unchanged.</p>
     *
     * @param destination destination to register
     * @param maxPendingBytes maximum queued payload bytes
     * @return existing or newly created queue
     */
    DispatcherQueue createQueue(Destination destination, long maxPendingBytes);

    /**
     * Deletes the queue for the destination.
     *
     * <p>When {@code force} is {@code false}, implementations should reject
     * deletion of non-empty queues. When {@code force} is {@code true}, queued
     * messages may be dropped before the queue is removed.</p>
     *
     * @param destination destination to remove
     * @param force whether queued messages may be dropped
     * @return deletion outcome
     */
    DispatcherQueueDeleteResult deleteQueue(Destination destination, boolean force);
}
