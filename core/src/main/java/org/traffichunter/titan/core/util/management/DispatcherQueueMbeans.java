package org.traffichunter.titan.core.util.management;

import java.lang.management.ManagementFactory;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/**
 * Utility for exposing dispatcher queues through the platform MBean server.
 *
 * <p>Queue object names are derived from the destination path and use
 * {@link ObjectName#quote(String)} so STOMP-style paths can be represented
 * safely in JMX. Registration replaces an existing queue MBean for the same
 * destination, while unregistration is tolerant when no MBean is present.</p>
 *
 * @author yun
 */
public final class DispatcherQueueMbeans {

    public static final String DOMAIN = "org.traffichunter.titan";
    public static final String TYPE = "DispatcherQueue";

    /** Name of a queue in the default group. */
    public static ObjectName objectName(String destination) {
        return objectName(DispatcherQueueMbean.DEFAULT_GROUP, destination);
    }

    public static ObjectName objectName(String group, String destination) {
        try {
            return new ObjectName(DOMAIN + ":type=" + TYPE
                    + ",group=" + ObjectName.quote(group)
                    + ",destination=" + ObjectName.quote(destination));
        } catch (JMException e) {
            throw new IllegalArgumentException(
                    "Invalid dispatcher queue name: group=" + group + ", destination=" + destination, e);
        }
    }

    public static ObjectName register(DispatcherQueueMbean queue) {
        return register(ManagementFactory.getPlatformMBeanServer(), queue);
    }

    public static ObjectName register(MBeanServer server, DispatcherQueueMbean queue) {
        ObjectName name = objectName(queue.getGroup(), queue.getDestination());
        try {
            StandardMBean mbean = new StandardMBean(queue, DispatcherQueueMbean.class);
            if (server.isRegistered(name)) {
                server.unregisterMBean(name);
            }
            server.registerMBean(mbean, name);
            return name;
        } catch (JMException e) {
            throw new IllegalStateException("Failed to register dispatcher queue MBean: " + name, e);
        }
    }

    /** Unregisters a queue in the default group. */
    public static void unregister(String destination) {
        unregister(DispatcherQueueMbean.DEFAULT_GROUP, destination);
    }

    public static void unregister(String group, String destination) {
        unregister(ManagementFactory.getPlatformMBeanServer(), group, destination);
    }

    /** Unregisters a queue in the default group. */
    public static void unregister(MBeanServer server, String destination) {
        unregister(server, DispatcherQueueMbean.DEFAULT_GROUP, destination);
    }

    public static void unregister(MBeanServer server, String group, String destination) {
        ObjectName name = objectName(group, destination);
        try {
            if (server.isRegistered(name)) {
                server.unregisterMBean(name);
            }
        } catch (JMException e) {
            throw new IllegalStateException("Failed to unregister dispatcher queue MBean: " + name, e);
        }
    }

    private DispatcherQueueMbeans() {
    }
}
