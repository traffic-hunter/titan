/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
                        attribute(name, "Capacity", Integer.class),
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
