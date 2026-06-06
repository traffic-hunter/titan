package org.traffichunter.titan.monitor.jmx.queue;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import org.traffichunter.titan.core.util.mbeans.DispatcherQueueMbeans;
import org.traffichunter.titan.monitor.model.QueueSnapshot;

public final class JmxDispatcherQueueCollector {

    private final MBeanServerConnection server;

    public JmxDispatcherQueueCollector() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    public JmxDispatcherQueueCollector(MBeanServerConnection server) {
        this.server = server;
    }

    public List<QueueSnapshot> collect() {
        try {
            ObjectName query = new ObjectName(DispatcherQueueMbeans.DOMAIN + ":type=" + DispatcherQueueMbeans.TYPE + ",*");
            List<QueueSnapshot> queues = new ArrayList<>();
            for (ObjectName name : server.queryNames(query, null)) {
                queues.add(new QueueSnapshot(
                        attribute(name, "Destination", String.class),
                        attribute(name, "Size", Integer.class),
                        attribute(name, "Capacity", Integer.class),
                        attribute(name, "Paused", Boolean.class)
                ));
            }
            queues.sort(Comparator.comparing(QueueSnapshot::destination));
            return List.copyOf(queues);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to collect dispatcher queue MBeans", e);
        }
    }

    private <T> T attribute(ObjectName name, String attribute, Class<T> type) throws Exception {
        return type.cast(server.getAttribute(name, attribute));
    }
}
