package org.traffichunter.titan.monitor.jmx.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.dispatch.SlowConsumerMetrics;

/**
 * @author yun
 */
class JmxSlowConsumerCollectorTest {

    @Test
    void collect_skipped_message_count_from_jmx() {
        SlowConsumerMetrics metrics = SlowConsumerMetrics.global();
        long before = metrics.getSkippedMessages();

        metrics.recordSkippedMessage();

        JmxSlowConsumerCollector collector = new JmxSlowConsumerCollector(
                ManagementFactory.getPlatformMBeanServer()
        );
        assertThat(collector.collect()).isEqualTo(before + 1);
    }
}
