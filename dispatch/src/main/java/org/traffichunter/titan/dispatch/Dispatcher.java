/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.dispatch;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * Registry of destination queues.
 *
 * <p>Dispatchers do not deliver messages directly. They resolve the queue for a destination,
 * creating it when necessary, and queue consumers perform the actual dispatch from
 * {@link DispatcherQueue}.</p>
 *
 * <p>{@link DestinationGroupRegistry} is the default implementation. It keeps queues in
 * named {@link DestinationGroup}s. Each group is itself a {@code Dispatcher} over the
 * queues it owns.</p>
 *
 * <p>Methods that take only a {@link Destination} address the default group. The
 * overloads that also take a group name reach other groups. A dispatcher that has no
 * notion of groups serves the default group and refuses every other name, so a message
 * aimed at an unknown group fails instead of landing in the wrong queue.</p>
 *
 * @author yungwang-o
 */
public interface Dispatcher {

    /**
     * Returns Titan's default destination registry implementation.
     *
     * <p>The default is a {@link DestinationGroupRegistry}. Queues created through
     * {@link #getOrPut(Destination)} belong to its default group.</p>
     */
    static Dispatcher getDefault() {
        return new DestinationGroupRegistry();
    }

    /** Returns the default destination registry with an automatic queue byte limit. */
    static Dispatcher getDefault(long maxPendingBytes) {
        return new DestinationGroupRegistry(maxPendingBytes);
    }

    /** Returns the default destination registry with byte pause and resume thresholds. */
    static Dispatcher getDefault(long maxPendingBytes, long resumePendingBytes) {
        return new DestinationGroupRegistry(maxPendingBytes, resumePendingBytes);
    }

    /**
     * Returns the queue for the destination, or {@code null} when it has not been created.
     */
    @Nullable DispatcherQueue get(Destination destination);

    /**
     * Returns the existing queue or creates one for this destination.
     */
    @CanIgnoreReturnValue
    DispatcherQueue getOrPut(Destination destination);

    /**
     * Returns the queue for the destination inside the named group, or {@code null} when
     * it has not been created. Looking up a group never creates it.
     *
     * @throws UnsupportedOperationException when this dispatcher cannot serve the group
     */
    default @Nullable DispatcherQueue get(String group, Destination destination) {
        if (DestinationGroups.isDefault(group)) {
            return get(destination);
        }
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " has no destination group " + group);
    }

    /**
     * Returns the existing queue or creates one for this destination inside the named
     * group. Implementations that manage groups create the group on first use.
     *
     * @throws UnsupportedOperationException when this dispatcher cannot serve the group
     */
    @CanIgnoreReturnValue
    default DispatcherQueue getOrPut(String group, Destination destination) {
        if (DestinationGroups.isDefault(group)) {
            return getOrPut(destination);
        }
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " has no destination group " + group);
    }

    /**
     * Returns the existing queue or creates one with the requested byte limit.
     *
     * <p>If the queue already exists, implementations should return it without
     * changing its byte limit.</p>
     */
    @CanIgnoreReturnValue
    DispatcherQueue getOrPut(Destination destination, long maxPendingBytes);

    /**
     * Returns queues matching the destination pattern.
     *
     * <p>Exact destinations return at most one queue. Wildcard destinations such as
     * {@code /queue/orders/*} return descendant queues under that prefix.</p>
     */
    List<DispatcherQueue> searchAll(Destination destination);

    boolean exists(Destination destination);

    void remove(Destination destination);

    /**
     * Removes the queue only while it is still the one registered for its destination.
     *
     * <p>Deleting by destination alone can take out a queue created since the caller looked
     * one up. A caller that holds the queue it means to delete passes it here, and a
     * {@code false} result says a newer queue now serves that destination. The queue carries
     * its own group and destination, so this reaches any group.</p>
     *
     * @return {@code true} when the queue was present and removed
     */
    boolean remove(DispatcherQueue expected);
}
