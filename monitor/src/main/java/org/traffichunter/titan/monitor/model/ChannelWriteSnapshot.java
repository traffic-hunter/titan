package org.traffichunter.titan.monitor.model;

/**
 * Aggregate outbound channel buffer state at the time of a monitoring snapshot.
 *
 * @author yun
 */
public record ChannelWriteSnapshot(
        int activeBuffers,
        long pendingBytes,
        int nonWritableBuffers
) {
}
