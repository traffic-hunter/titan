package org.traffichunter.titan.core.util.management;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/**
 * Utility for exposing dispatcher queues through the platform MBean server.
 *
 * <p>Queue object names are derived from the group and destination path and use
 * {@link ObjectName#quote(String)} so STOMP-style paths can be represented
 * safely in JMX. Registration replaces an existing queue MBean for the same
 * name, while unregistration is tolerant when no MBean is present.</p>
 *
 * <p>A deleted queue and its replacement share one object name, so this class remembers
 * which queue each name currently holds. {@link #unregister(DispatcherQueueMbean)} acts
 * only while the name still belongs to that queue, which keeps a late removal from taking
 * the replacement's MBean with it.</p>
 *
 * @author yun
 */
public final class DispatcherQueueMbeans {

    public static final String DOMAIN = "org.traffichunter.titan";
    public static final String TYPE = "DispatcherQueue";

    /** Queue currently holding each object name, per server. Guarded by its own monitor. */
    private static final Map<MBeanServer, Map<ObjectName, DispatcherQueueMbean>> REGISTERED =
            new WeakHashMap<>();

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
        synchronized (REGISTERED) {
            try {
                StandardMBean mbean = new StandardMBean(queue, DispatcherQueueMbean.class);
                if (server.isRegistered(name)) {
                    server.unregisterMBean(name);
                }
                server.registerMBean(mbean, name);
            } catch (JMException e) {
                throw new IllegalStateException("Failed to register dispatcher queue MBean: " + name, e);
            }
            REGISTERED.computeIfAbsent(server, ignored -> new HashMap<>()).put(name, queue);
        }
        return name;
    }

    /** Unregisters the queue while it still holds its object name. */
    public static void unregister(DispatcherQueueMbean queue) {
        unregister(ManagementFactory.getPlatformMBeanServer(), queue);
    }

    /** Unregisters the queue while it still holds its object name. */
    public static void unregister(MBeanServer server, DispatcherQueueMbean queue) {
        ObjectName name = objectName(queue.getGroup(), queue.getDestination());
        synchronized (REGISTERED) {
            Map<ObjectName, DispatcherQueueMbean> names = REGISTERED.get(server);
            if (names == null || names.get(name) != queue) {
                // A queue created since this one left the dispatcher owns the name now.
                return;
            }
            forget(server, names, name);
            doUnregister(server, name);
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
        synchronized (REGISTERED) {
            Map<ObjectName, DispatcherQueueMbean> names = REGISTERED.get(server);
            if (names != null) {
                forget(server, names, name);
            }
            doUnregister(server, name);
        }
    }

    private static void forget(
            MBeanServer server,
            Map<ObjectName, DispatcherQueueMbean> names,
            ObjectName name
    ) {
        names.remove(name);
        if (names.isEmpty()) {
            REGISTERED.remove(server);
        }
    }

    private static void doUnregister(MBeanServer server, ObjectName name) {
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
