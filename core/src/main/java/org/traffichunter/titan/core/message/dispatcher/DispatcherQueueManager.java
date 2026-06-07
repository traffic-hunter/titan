package org.traffichunter.titan.core.message.dispatcher;

import org.traffichunter.titan.core.util.Destination;

/**
 * Management boundary for dispatcher queues owned by a running transport or
 * fanout component.
 *
 * <p>This interface is intentionally narrower than {@link Dispatcher}. It is
 * used by operational surfaces, such as the monitor HTTP API, when they need to
 * create or remove queues without depending on the concrete fanout
 * implementation.</p>
 *
 * @author yungwang-o
 */
public interface DispatcherQueueManager {

    /**
     * Creates the queue for the destination if it does not exist.
     *
     * <p>Implementations should be idempotent. When the queue already exists,
     * they should return the existing queue and leave its original capacity
     * unchanged.</p>
     *
     * @param destination destination to register
     * @param capacity requested capacity for a newly created queue
     * @return existing or newly created queue
     */
    DispatcherQueue createQueue(Destination destination, int capacity);

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
