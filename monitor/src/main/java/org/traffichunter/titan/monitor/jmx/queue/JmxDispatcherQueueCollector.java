package org.traffichunter.titan.monitor.jmx.queue;

import java.util.List;
import javax.management.MBeanServerConnection;
import org.traffichunter.titan.core.util.management.QueueResource;
import org.traffichunter.titan.core.util.management.QueueResourceDetector;
import org.traffichunter.titan.core.util.management.ResourceDetector;
import org.traffichunter.titan.monitor.model.QueueSnapshot;

public final class JmxDispatcherQueueCollector {

    private final ResourceDetector<List<QueueResource>> resourceDetector;

    public JmxDispatcherQueueCollector() {
        this(new QueueResourceDetector());
    }

    public JmxDispatcherQueueCollector(MBeanServerConnection server) {
        this(new QueueResourceDetector(server));
    }

    public JmxDispatcherQueueCollector(ResourceDetector<List<QueueResource>> resourceDetector) {
        this.resourceDetector = resourceDetector;
    }

    public List<QueueSnapshot> collect() {
        return resourceDetector.detect().stream()
                .map(queue -> new QueueSnapshot(
                        queue.destination(),
                        queue.size(),
                        queue.pendingBytes(),
                        queue.maxPendingBytes(),
                        queue.paused()
                ))
                .toList();
    }
}
