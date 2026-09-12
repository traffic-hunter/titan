package org.traffichunter.titan.monitor.http;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.dispatch.DestinationGroupRegistry;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.dispatch.DispatcherQueueDeleteResult;
import org.traffichunter.titan.dispatch.DispatcherQueueManager;

/**
 * Queue manager backed by a real group registry, so a request that names a group reaches
 * the queues of that group and no other.
 */
@NullMarked
final class TestQueueManager implements DispatcherQueueManager {

    private final DestinationGroupRegistry dispatcher = new DestinationGroupRegistry();

    /** Reads a queue directly, so a test can check what an HTTP call did or did not touch. */
    @Nullable DispatcherQueue queue(String group, Destination destination) {
        return dispatcher.get(group, destination);
    }

    @Override
    public DispatcherQueue createQueue(String group, Destination destination, long maxPendingBytes) {
        return dispatcher.getOrPut(group, destination, maxPendingBytes);
    }

    @Override
    public boolean pauseQueue(String group, Destination destination) {
        DispatcherQueue queue = dispatcher.get(group, destination);
        if (queue == null) {
            return false;
        }
        queue.pause();
        return true;
    }

    @Override
    public boolean resumeQueue(String group, Destination destination) {
        DispatcherQueue queue = dispatcher.get(group, destination);
        if (queue == null) {
            return false;
        }
        queue.resume();
        return true;
    }

    @Override
    public boolean purgeQueue(String group, Destination destination) {
        DispatcherQueue queue = dispatcher.get(group, destination);
        if (queue == null) {
            return false;
        }
        queue.clear();
        return true;
    }

    @Override
    public DispatcherQueueDeleteResult deleteQueue(String group, Destination destination, boolean force) {
        DispatcherQueue queue = dispatcher.get(group, destination);
        if (queue == null) {
            return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.NOT_FOUND, 0);
        }
        int size = queue.size();
        if (size > 0 && !force) {
            return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.NOT_EMPTY, size);
        }
        if (force) {
            queue.clear();
        }
        // Removing the queue itself also unregisters the MBean of that exact queue.
        dispatcher.remove(queue);
        return new DispatcherQueueDeleteResult(DispatcherQueueDeleteResult.Status.DELETED, size);
    }
}
