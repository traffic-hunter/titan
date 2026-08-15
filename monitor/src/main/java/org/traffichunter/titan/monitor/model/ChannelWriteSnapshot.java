package org.traffichunter.titan.monitor.model;

/**
 * Process-wide outbound channel pressure at the time a monitoring snapshot is collected.
 *
 * @param activeBuffers number of open channel write buffers
 * @param pendingBytes bytes not yet written to network sockets
 * @param nonWritableBuffers buffers currently held above their high watermark
 * @author yun
 */
public record ChannelWriteSnapshot(
        int activeBuffers,
        long pendingBytes,
        int nonWritableBuffers
) {
}
