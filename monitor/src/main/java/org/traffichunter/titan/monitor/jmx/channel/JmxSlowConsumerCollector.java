package org.traffichunter.titan.monitor.jmx.channel;

import java.lang.management.ManagementFactory;
import java.util.function.LongSupplier;
import javax.management.MBeanServerConnection;
import org.traffichunter.titan.dispatch.SlowConsumerMetricsMbeans;

/**
 * Reads the process-wide slow-consumer skip counter from JMX.
 *
 * @author yun
 */
public final class JmxSlowConsumerCollector {

    private final LongSupplier skippedMessages;

    public JmxSlowConsumerCollector() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    public JmxSlowConsumerCollector(MBeanServerConnection server) {
        this(() -> collect(server));
    }

    public JmxSlowConsumerCollector(LongSupplier skippedMessages) {
        this.skippedMessages = skippedMessages;
    }

    public long collect() {
        return skippedMessages.getAsLong();
    }

    private static long collect(MBeanServerConnection server) {
        try {
            if (!server.isRegistered(SlowConsumerMetricsMbeans.objectName())) {
                return 0;
            }
            return (Long) server.getAttribute(SlowConsumerMetricsMbeans.objectName(), "SkippedMessages");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to collect slow consumer metrics", e);
        }
    }
}
