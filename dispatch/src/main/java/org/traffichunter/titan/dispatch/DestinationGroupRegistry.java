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
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbean;

/**
 * Top level {@link Dispatcher} composed of named {@link DestinationGroup}s.
 *
 * <p>Groups are namespaces. The same destination may exist in several groups as
 * separate queues, and the registry holds no queues itself. As a {@code Dispatcher} the
 * registry is the {@value #DEFAULT_GROUP} namespace: calls that name no group read and
 * create queues there. Other groups are reached through {@link #getOrPutGroup(String)}
 * or the overloads that take a group name. The default group always exists and cannot
 * be removed.</p>
 *
 * @author yun
 */
public final class DestinationGroupRegistry implements Dispatcher {

    public static final String DEFAULT_GROUP = DispatcherQueueMbean.DEFAULT_GROUP;

    private final Map<String, DispatcherDestinationGroup> groups = new ConcurrentHashMap<>();
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
        if (DEFAULT_GROUP.equals(name) || !groups.containsKey(name)) {
            return false;
        }
        // computeIfPresent returns null for a missing key as well as for a removed entry.
        // The containsKey check above is what lets null mean "removed" here.
        DispatcherDestinationGroup destinationGroup =
                groups.computeIfPresent(name, (groupName, group) -> group.tryRemove() ? null : group);

        return destinationGroup == null;
    }

    public List<DestinationGroup> groups() {
        return List.copyOf(groups.values());
    }

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        return defaultGroup.get(destination);
    }

    @Override
    public DispatcherQueue getOrPut(Destination destination) {
        return defaultGroup.getOrPut(destination);
    }

    @Override
    public DispatcherQueue getOrPut(Destination destination, long maxPendingBytes) {
        return defaultGroup.getOrPut(destination, maxPendingBytes);
    }

    /** Reads inside the named group. An unknown group yields {@code null} and is not created. */
    @Override
    public @Nullable DispatcherQueue get(String group, Destination destination) {
        DispatcherDestinationGroup destinationGroup = groups.get(group);
        return destinationGroup == null ? null : destinationGroup.get(destination);
    }

    /** Creates the group on first use, then the queue inside it. */
    @Override
    public DispatcherQueue getOrPut(String group, Destination destination) {
        return groupOrCreate(group).getOrPut(destination);
    }

    @Override
    public List<DispatcherQueue> searchAll(Destination destination) {
        return defaultGroup.searchAll(destination);
    }

    @Override
    public boolean exists(Destination destination) {
        return defaultGroup.exists(destination);
    }

    @Override
    public void remove(Destination destination) {
        defaultGroup.remove(destination);
    }

    private DispatcherDestinationGroup groupOrCreate(String name) {
        Assert.checkArgument(DestinationGroups.isValid(name), "Invalid group name: " + name);
        return groups.computeIfAbsent(name, groupName ->
                new DispatcherDestinationGroup(groupName, defaultMaxPendingBytes, defaultResumePendingBytes));
    }
}
