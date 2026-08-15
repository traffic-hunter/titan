package org.traffichunter.titan.monitor.jmx.channel;

import javax.management.MBeanServerConnection;
import org.traffichunter.titan.core.util.management.ChannelWriteBufferResource;
import org.traffichunter.titan.core.util.management.ChannelWriteBufferResourceDetector;
import org.traffichunter.titan.core.util.management.ResourceDetector;
import org.traffichunter.titan.monitor.model.ChannelWriteSnapshot;

/**
 * Converts channel write buffer resource measurements into monitor snapshots.
 *
 * @author yun
 */
public final class JmxChannelWriteBufferCollector {

    private final ResourceDetector<ChannelWriteBufferResource> resourceDetector;

    public JmxChannelWriteBufferCollector() {
        this(new ChannelWriteBufferResourceDetector());
    }

    public JmxChannelWriteBufferCollector(MBeanServerConnection server) {
        this(new ChannelWriteBufferResourceDetector(server));
    }

    public JmxChannelWriteBufferCollector(ResourceDetector<ChannelWriteBufferResource> resourceDetector) {
        this.resourceDetector = resourceDetector;
    }

    public ChannelWriteSnapshot collect() {
        ChannelWriteBufferResource resource = resourceDetector.detect();
        return new ChannelWriteSnapshot(
                resource.activeBuffers(),
                resource.pendingBytes(),
                resource.nonWritableBuffers()
        );
    }
}
