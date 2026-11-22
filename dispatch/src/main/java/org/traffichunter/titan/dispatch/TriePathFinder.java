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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Trie;
import org.traffichunter.titan.core.util.TrieImpl;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbeans;

/**
 * Trie-backed path finder for path-like destinations.
 *
 * <p>Destinations are stored by their normalized path. A trie keeps wildcard search and prefix
 * existence checks aligned with the routing model used by queue creation and fanout consumers.
 * Exact lookups read a map keyed by the full path, which holds the same queues as the trie.</p>
 *
 * @author yungwang-o
 */
public class TriePathFinder implements PathFinder {

    private static final Logger log = LoggerFactory.getLogger(TriePathFinder.class);

    private final Trie<DispatcherQueue> trie = new TrieImpl<>();
    private final Map<String, DispatcherQueue> queues = new ConcurrentHashMap<>();
    // Held while a queue is added or removed, so the trie and the map change together.
    private final Lock lock = new ReentrantLock();
    private final long defaultMaxPendingBytes;
    private final long defaultResumePendingBytes;
    private final String group;
    // Read and written under the lock.
    private DispatcherQueueListener listener = DispatcherQueueListener.NOOP;

    public TriePathFinder() {
        this(DispatcherQueue.DEFAULT_MAX_PENDING_BYTES);
    }

    public TriePathFinder(long defaultMaxPendingBytes) {
        this(
                defaultMaxPendingBytes,
                DestinationQueueMetadata.defaultResumePendingBytes(defaultMaxPendingBytes)
        );
    }

    public TriePathFinder(long defaultMaxPendingBytes, long defaultResumePendingBytes) {
        this(DispatcherQueue.DEFAULT_GROUP, defaultMaxPendingBytes, defaultResumePendingBytes);
    }

    /** PathFinder whose queues belong to the named group. */
    public TriePathFinder(String group, long defaultMaxPendingBytes, long defaultResumePendingBytes) {
        DestinationQueueMetadata.validateThresholds(defaultMaxPendingBytes, defaultResumePendingBytes);
        this.group = group;
        this.defaultMaxPendingBytes = defaultMaxPendingBytes;
        this.defaultResumePendingBytes = defaultResumePendingBytes;
    }

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        return queues.get(destination.path());
    }

    @Override
    public DispatcherQueue getOrPut(final Destination destination) {
        DispatcherQueue queue = get(destination);
        if (queue != null) {
            return queue;
        }
        return create(destination, defaultMaxPendingBytes, defaultResumePendingBytes);
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
        DispatcherQueue queue = get(destination);
        if (queue != null) {
            return queue;
        }
        return create(
                destination,
                maxPendingBytes,
                DestinationQueueMetadata.defaultResumePendingBytes(maxPendingBytes)
        );
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
        String path = destination.path();
        DispatcherQueue queue;

        lock.lock();
        try {
            queue = queues.remove(path);
            if (queue != null) {
                trie.remove(path, queue);
                listener.queueRemoved(queue);
            }
        } finally {
            lock.unlock();
        }

        if (queue != null) {
            DispatcherQueueMbeans.unregister(queue);
        }
    }

    @Override
    public boolean remove(DispatcherQueue expected) {
        String path = expected.route().path();

        lock.lock();
        try {
            if (!queues.remove(path, expected)) {
                return false;
            }
            trie.remove(path, expected);
            listener.queueRemoved(expected);
        } finally {
            lock.unlock();
        }

        DispatcherQueueMbeans.unregister(expected);
        return true;
    }

    /** {@code true} when this path finder holds no queues. */
    @Override
    public void listen(DispatcherQueueListener listener) {
        lock.lock();
        try {
            this.listener = listener;
            queues.values().forEach(listener::queueCreated);
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return trie.isEmpty();
    }

    // Another thread may have created the queue since the lookup, so the map is read again
    // under the lock.
    private DispatcherQueue create(Destination destination, long maxPendingBytes, long resumePendingBytes) {
        String path = destination.path();

        lock.lock();
        try {
            DispatcherQueue existing = queues.get(path);
            if (existing != null) {
                return existing;
            }

            DispatcherQueue queue = DispatcherQueue.create(destination, maxPendingBytes, resumePendingBytes, group);
            trie.insert(path, queue);
            queues.put(path, queue);
            listener.queueCreated(queue);
            log.info("Created new dispatcher for path {} in group {}", path, group);
            return queue;
        } finally {
            lock.unlock();
        }
    }

    private void requireOwnGroup(String group) {
        if (!this.group.equals(group)) {
            throw new UnsupportedOperationException(
                    "TriePathFinder for group " + this.group + " cannot serve group " + group);
        }
    }
}
