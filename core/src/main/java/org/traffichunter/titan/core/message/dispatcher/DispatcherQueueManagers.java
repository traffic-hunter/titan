package org.traffichunter.titan.core.message.dispatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Process-local registry of dispatcher queue managers.
 *
 * <p>Fanout adapters register their running gateway under the managed server
 * name. Runtime extensions, such as the monitor module, can then resolve a
 * manager without taking a compile-time dependency on the fanout module.</p>
 *
 * @author yungwang-o
 */
public final class DispatcherQueueManagers {

    private static final Map<String, DispatcherQueueManager> MANAGERS = new ConcurrentHashMap<>();

    /**
     * Registers or replaces the queue manager for a managed server name.
     */
    public static void register(String name, DispatcherQueueManager manager) {
        MANAGERS.put(name, manager);
    }

    /**
     * Removes the queue manager for a managed server name.
     */
    public static void unregister(String name) {
        MANAGERS.remove(name);
    }

    /**
     * Returns the queue manager for a managed server name, or {@code null} when
     * no manager has been registered.
     */
    public static @Nullable DispatcherQueueManager get(String name) {
        return MANAGERS.get(name);
    }

    /**
     * Returns the only registered queue manager.
     *
     * <p>When zero or multiple managers exist, callers must provide an explicit
     * server name and this method returns {@code null}.</p>
     */
    public static @Nullable DispatcherQueueManager getDefault() {
        if (MANAGERS.size() != 1) {
            return null;
        }
        return MANAGERS.values().iterator().next();
    }

    /**
     * Returns registered managed server names in stable order.
     */
    public static List<String> names() {
        return MANAGERS.keySet().stream().sorted().toList();
    }

    private DispatcherQueueManagers() {
    }
}
