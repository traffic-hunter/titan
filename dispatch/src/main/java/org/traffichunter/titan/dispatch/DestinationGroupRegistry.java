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
package org.traffichunter.titan.dispatch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;

/**
 * Top level {@link Dispatcher} composed of named {@link DestinationGroup}s.
 *
 * <p>The registry holds no queues itself. Every queue lives in one group. The registry
 * keeps a destination to group index for lookups across groups and to reject a
 * destination that is already registered elsewhere. Queues created without a group go to
 * {@value #DEFAULT_GROUP}, which always exists and cannot be removed.</p>
 *
 * <p>Lookups take no lock. Registration and removal share one lock so the group
 * dispatcher and the index change together.</p>
 *
 * @author yun
 */
public final class DestinationGroupRegistry implements Dispatcher {

    public static final String DEFAULT_GROUP = "default";

    private final Map<String, DispatcherDestinationGroup> groups = new ConcurrentHashMap<>();
    private final Map<Destination, String> owners = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final long defaultMaxPendingBytes;
    private final long defaultResumePendingBytes;
    private final DispatcherDestinationGroup defaultGroup;

    public DestinationGroupRegistry() {
        this(DispatcherQueue.DEFAULT_MAX_PENDING_BYTES);
    }

    public DestinationGroupRegistry(long defaultMaxPendingBytes) {
        this(
                defaultMaxPendingBytes,
                DestinationQueueMetadata.defaultResumePendingBytes(defaultMaxPendingBytes)
        );
    }

    public DestinationGroupRegistry(long defaultMaxPendingBytes, long defaultResumePendingBytes) {
        DestinationQueueMetadata.validateThresholds(defaultMaxPendingBytes, defaultResumePendingBytes);
        this.defaultMaxPendingBytes = defaultMaxPendingBytes;
        this.defaultResumePendingBytes = defaultResumePendingBytes;
        this.defaultGroup = groupOrCreate(DEFAULT_GROUP);
    }

    /**
     * Returns the named group, creating it on first use. New groups inherit the
     * registry's byte thresholds.
     */
    public DestinationGroup getOrPutGroup(String name) {
        return groupOrCreate(name);
    }

    public @Nullable DestinationGroup getGroup(String name) {
        return groups.get(name);
    }

    public boolean containsGroup(String name) {
        return groups.containsKey(name);
    }

    /**
     * Removes an empty group.
     *
     * <p>A group that still owns a queue is kept, because removing it would drop queued
     * messages. The default group is kept as well.</p>
     *
     * @return {@code true} when the group existed and was removed
     */
    public boolean removeGroup(String name) {
        if (DEFAULT_GROUP.equals(name)) {
            return false;
        }

        lock.lock();
        try {
            if (!groups.containsKey(name) || owners.containsValue(name)) {
                return false;
            }
            groups.remove(name);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public List<DestinationGroup> groups() {
        return List.copyOf(groups.values());
    }

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        String owner = owners.get(destination);
        if (owner == null) {
            return null;
        }
        DispatcherDestinationGroup group = groups.get(owner);
        return group == null ? null : group.getLocal(destination);
    }

    /**
     * Returns the destination's queue from the group that owns it. Creates the queue in
     * the default group when no group has it.
     */
    @Override
    public DispatcherQueue getOrPut(Destination destination) {
        return getOrCreate(destination, null, defaultGroup, false);
    }

    @Override
    public DispatcherQueue getOrPut(Destination destination, long maxPendingBytes) {
        return getOrCreate(destination, maxPendingBytes, defaultGroup, false);
    }

    @Override
    public List<DispatcherQueue> searchAll(Destination destination) {
        if (!destination.path().endsWith("/*")) {
            DispatcherQueue queue = get(destination);
            return queue == null ? List.of() : List.of(queue);
        }
        return groups.values().stream()
                .flatMap(group -> group.searchAllLocal(destination).stream())
                .toList();
    }

    @Override
    public boolean exists(Destination destination) {
        return groups.values().stream().anyMatch(group -> group.existsLocal(destination));
    }

    @Override
    public void remove(Destination destination) {
        lock.lock();
        try {
            // Remove the index entry before the queue. A reader without the lock then
            // never sees an owner whose dispatcher no longer has the queue.
            String owner = owners.remove(destination);
            if (owner == null) {
                return;
            }
            DispatcherDestinationGroup group = groups.get(owner);
            if (group != null) {
                group.removeLocal(destination);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Registers a destination for a group. Fails when another group already owns the
     * destination.
     */
    DispatcherQueue register(
            DispatcherDestinationGroup group,
            Destination destination,
            @Nullable Long maxPendingBytes
    ) {
        return getOrCreate(destination, maxPendingBytes, group, true);
    }

    /** Removes a destination only when the calling group owns it. */
    void removeFrom(DispatcherDestinationGroup group, Destination destination) {
        lock.lock();
        try {
            if (!group.name().equals(owners.get(destination))) {
                return;
            }
            owners.remove(destination);
            group.removeLocal(destination);
        } finally {
            lock.unlock();
        }
    }

    private DispatcherQueue getOrCreate(
            Destination destination,
            @Nullable Long maxPendingBytes,
            DispatcherDestinationGroup target,
            boolean rejectForeignOwner
    ) {
        // Routing calls this for every message. Find an existing queue without taking
        // the lock.
        DispatcherQueue existing = existing(destination, target, rejectForeignOwner);
        if (existing != null) {
            return existing;
        }

        lock.lock();
        try {
            existing = existing(destination, target, rejectForeignOwner);
            if (existing != null) {
                return existing;
            }

            DispatcherQueue queue = target.getOrPutLocal(destination, maxPendingBytes);
            owners.put(destination, target.name());
            return queue;
        } finally {
            lock.unlock();
        }
    }

    private @Nullable DispatcherQueue existing(
            Destination destination,
            DispatcherDestinationGroup target,
            boolean rejectForeignOwner
    ) {
        String owner = owners.get(destination);
        if (owner == null) {
            return null;
        }
        DispatcherDestinationGroup group = groups.get(owner);
        if (group == null) {
            return null;
        }
        DispatcherQueue queue = group.getLocal(destination);
        if (queue == null) {
            return null;
        }
        if (rejectForeignOwner && group != target) {
            throw new IllegalStateException(
                    "Destination " + destination.path() + " already belongs to group " + owner
                            + " and cannot be registered in group " + target.name()
            );
        }
        return queue;
    }

    private DispatcherDestinationGroup groupOrCreate(String name) {
        Assert.checkArgument(!name.isBlank(), "group name must not be blank");
        return groups.computeIfAbsent(name, groupName -> new DispatcherDestinationGroup(
                groupName,
                this,
                defaultMaxPendingBytes,
                defaultResumePendingBytes
        ));
    }
}
