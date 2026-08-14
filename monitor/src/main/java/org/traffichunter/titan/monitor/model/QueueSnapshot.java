package org.traffichunter.titan.monitor.model;

public record QueueSnapshot(
        String destination,
        int size,
        long pendingBytes,
        long maxPendingBytes,
        long resumePendingBytes,
        boolean paused
) {
}
