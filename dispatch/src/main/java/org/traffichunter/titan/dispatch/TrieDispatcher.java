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
package org.traffichunter.titan.dispatch;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Trie;
import org.traffichunter.titan.core.util.TrieImpl;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbeans;

/**
 * Trie-backed dispatcher for path-like destinations.
 *
 * <p>Destinations are stored by their normalized path. A trie keeps lookup and prefix existence
 * checks aligned with the routing model used by queue creation and fanout consumers.</p>
 *
 * @author yungwang-o
 */
public class TrieDispatcher implements Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(TrieDispatcher.class);

    private final Trie<DispatcherQueue> trie = new TrieImpl<>();
    private final long defaultMaxPendingBytes;
    private final long defaultResumePendingBytes;
    private final String group;

    public TrieDispatcher() {
        this(DispatcherQueue.DEFAULT_MAX_PENDING_BYTES);
    }

    public TrieDispatcher(long defaultMaxPendingBytes) {
        this(
                defaultMaxPendingBytes,
                DestinationQueueMetadata.defaultResumePendingBytes(defaultMaxPendingBytes)
        );
    }

    public TrieDispatcher(long defaultMaxPendingBytes, long defaultResumePendingBytes) {
        this(DispatcherQueue.DEFAULT_GROUP, defaultMaxPendingBytes, defaultResumePendingBytes);
    }

    /** Dispatcher whose queues belong to the named group. */
    public TrieDispatcher(String group, long defaultMaxPendingBytes, long defaultResumePendingBytes) {
        DestinationQueueMetadata.validateThresholds(defaultMaxPendingBytes, defaultResumePendingBytes);
        this.group = group;
        this.defaultMaxPendingBytes = defaultMaxPendingBytes;
        this.defaultResumePendingBytes = defaultResumePendingBytes;
    }

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        return trie.get(destination.path());
    }

    @Override
    public DispatcherQueue getOrPut(final Destination destination) {
        return trie.computeIfAbsent(destination.path(), path -> {
            DispatcherQueue queue = DispatcherQueue.create(
                    destination,
                    defaultMaxPendingBytes,
                    defaultResumePendingBytes,
                    group
            );
            log.info("Created new dispatcher for path {} in group {}", path, group);
            return queue;
        });
    }

    @Override
    public @Nullable DispatcherQueue get(String group, Destination destination) {
        requireOwnGroup(group);
        return get(destination);
    }

    @Override
    public DispatcherQueue getOrPut(String group, Destination destination) {
        requireOwnGroup(group);
        return getOrPut(destination);
    }

    @Override
    public DispatcherQueue getOrPut(String group, Destination destination, long maxPendingBytes) {
        requireOwnGroup(group);
        return getOrPut(destination, maxPendingBytes);
    }

    @Override
    public DispatcherQueue getOrPut(final Destination destination, long maxPendingBytes) {
        return trie.computeIfAbsent(destination.path(), path -> {
            DispatcherQueue queue = DispatcherQueue.create(
                    destination,
                    maxPendingBytes,
                    DestinationQueueMetadata.defaultResumePendingBytes(maxPendingBytes),
                    group
            );
            log.info("Created new dispatcher for path {} in group {}", path, group);
            return queue;
        });
    }

    @Override
    public List<DispatcherQueue> searchAll(Destination destination) {
        String path = destination.path();
        if (!path.endsWith("/*")) {
            DispatcherQueue queue = get(destination);
            return queue == null ? List.of() : List.of(queue);
        }
        return trie.searchAll(path);
    }

    @Override
    public boolean exists(final Destination destination) {
        return trie.startsWith(destination.path());
    }

    public boolean startsWith(final Destination destination) {
        return trie.startsWith(destination.path());
    }

    @Override
    public void remove(Destination destination) {
        DispatcherQueue queue = trie.remove(destination.path());
        if (queue != null) {
            DispatcherQueueMbeans.unregister(queue);
        }
    }

    @Override
    public boolean remove(DispatcherQueue expected) {
        if (!trie.remove(expected.route().path(), expected)) {
            return false;
        }
        DispatcherQueueMbeans.unregister(expected);
        return true;
    }

    /** {@code true} when this dispatcher holds no queues. */
    public boolean isEmpty() {
        return trie.isEmpty();
    }

    private void requireOwnGroup(String group) {
        if (!this.group.equals(group)) {
            throw new UnsupportedOperationException(
                    "TrieDispatcher for group " + this.group + " cannot serve group " + group);
        }
    }
}
