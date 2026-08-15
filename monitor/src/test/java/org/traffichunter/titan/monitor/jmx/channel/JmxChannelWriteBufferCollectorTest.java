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
    void collect_channel_write_buffer_snapshot() {
        JmxChannelWriteBufferCollector collector = new JmxChannelWriteBufferCollector(
                () -> new ChannelWriteBufferResource(5, 4096, 2)
        );

        ChannelWriteSnapshot snapshot = collector.collect();

        assertThat(snapshot).isEqualTo(new ChannelWriteSnapshot(5, 4096, 2));
    }
}
