package org.traffichunter.titan.monitor.jmx.channel;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.management.ChannelWriteBufferResource;
import org.traffichunter.titan.monitor.model.ChannelWriteSnapshot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class JmxChannelWriteBufferCollectorTest {

    @Test
    void collect_pending_bytes_above_integer_range_without_truncation() {
        long pendingBytes = (long) Integer.MAX_VALUE + 1024;
        JmxChannelWriteBufferCollector collector = new JmxChannelWriteBufferCollector(
                () -> new ChannelWriteBufferResource(1, pendingBytes, 1)
        );

        ChannelWriteSnapshot snapshot = collector.collect();

        assertThat(snapshot.pendingBytes()).isEqualTo(pendingBytes);
    }

    @Test
    void collect_channel_write_buffer_snapshot() {
        JmxChannelWriteBufferCollector collector = new JmxChannelWriteBufferCollector(
                () -> new ChannelWriteBufferResource(5, 4096, 2)
        );

        ChannelWriteSnapshot snapshot = collector.collect();

        assertThat(snapshot).isEqualTo(new ChannelWriteSnapshot(5, 4096, 2));
    }
}
