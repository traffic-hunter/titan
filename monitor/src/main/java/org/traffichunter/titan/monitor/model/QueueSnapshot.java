package org.traffichunter.titan.monitor.model;

import org.traffichunter.titan.core.util.management.DispatcherQueueMbean;

public record QueueSnapshot(
        String group,
        String destination,
        int size,
        long pendingBytes,
        long maxPendingBytes,
        long resumePendingBytes,
        boolean paused
) {

    /** Snapshot of a queue in the default group. */
    public QueueSnapshot(
            String destination,
            int size,
            long pendingBytes,
            long maxPendingBytes,
            long resumePendingBytes,
            boolean paused
    ) {
        this(DispatcherQueueMbean.DEFAULT_GROUP, destination, size, pendingBytes, maxPendingBytes, resumePendingBytes, paused);
    }
}
