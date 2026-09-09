/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.util.management;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * Detects dispatcher queue snapshots from their registered JMX MBeans.
 *
 * @author yun
 */
public final class QueueResourceDetector implements ResourceDetector<List<QueueResource>> {

    private final MBeanServerConnection server;

    /**
     * Creates a detector backed by the platform MBean server.
     */
    public QueueResourceDetector() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    /**
     * Creates a detector backed by the supplied MBean server connection.
     *
     * @param server MBean server connection used for queue measurements
     */
    public QueueResourceDetector(MBeanServerConnection server) {
        this.server = server;
    }

    @Override
    public List<QueueResource> detect() {
        try {
            ObjectName query = new ObjectName(
                    DispatcherQueueMbeans.DOMAIN + ":type=" + DispatcherQueueMbeans.TYPE + ",*"
            );
            List<QueueResource> queues = new ArrayList<>();
            for (ObjectName name : server.queryNames(query, null)) {
                queues.add(new QueueResource(
                        attribute(name, "Destination", String.class),
                        attribute(name, "Size", Integer.class),
                        attribute(name, "PendingBytes", Long.class),
                        attribute(name, "MaxPendingBytes", Long.class),
                        attribute(name, "ResumePendingBytes", Long.class),
                        attribute(name, "Paused", Boolean.class)
                ));
            }
            queues.sort(Comparator.comparing(QueueResource::destination));
            return List.copyOf(queues);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to detect dispatcher queue resources", e);
        }
    }

    private <T> T attribute(ObjectName name, String attribute, Class<T> type) throws Exception {
        Object value = server.getAttribute(name, attribute);
        if (value == null) {
            throw new IllegalStateException("Missing dispatcher queue MBean attribute: " + attribute);
        }
        return type.cast(value);
    }
}
