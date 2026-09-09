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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;

/**
 * Hash-map dispatcher implementation.
 *
 * <p>This implementation is useful when destinations should be matched exactly and prefix
 * behavior is unnecessary.</p>
 *
 * @author yungwang-o
 */
public class MapDispatcher implements Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(MapDispatcher.class);

    private final Map<Destination, DispatcherQueue> map;
    private final long defaultMaxPendingBytes;
    private final long defaultResumePendingBytes;

    public MapDispatcher(final int initialCapacity) {
        this(initialCapacity, DispatcherQueue.DEFAULT_MAX_PENDING_BYTES);
    }

    public MapDispatcher(final int initialCapacity, long defaultMaxPendingBytes) {
        this(
                new ConcurrentHashMap<>(initialCapacity),
                defaultMaxPendingBytes,
                DestinationQueueMetadata.defaultResumePendingBytes(defaultMaxPendingBytes)
        );
    }

    public MapDispatcher(final Map<Destination, DispatcherQueue> map) {
        this(map, DispatcherQueue.DEFAULT_MAX_PENDING_BYTES);
    }

    public MapDispatcher(final Map<Destination, DispatcherQueue> map, long defaultMaxPendingBytes) {
        this(map, defaultMaxPendingBytes, DestinationQueueMetadata.defaultResumePendingBytes(defaultMaxPendingBytes));
    }

    public MapDispatcher(
            final Map<Destination, DispatcherQueue> map,
            long defaultMaxPendingBytes,
            long defaultResumePendingBytes
    ) {
        DestinationQueueMetadata.validateThresholds(defaultMaxPendingBytes, defaultResumePendingBytes);
        this.map = map;
        this.defaultMaxPendingBytes = defaultMaxPendingBytes;
        this.defaultResumePendingBytes = defaultResumePendingBytes;
    }

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        return map.get(destination);
    }

    @Override
    public DispatcherQueue getOrPut(final Destination destination) {
        return map.computeIfAbsent(
                destination,
                key -> DispatcherQueue.create(key, defaultMaxPendingBytes, defaultResumePendingBytes)
        );
    }

    @Override
    public DispatcherQueue getOrPut(final Destination destination, long maxPendingBytes) {
        return map.computeIfAbsent(destination, key -> DispatcherQueue.create(key, maxPendingBytes));
    }

    @Override
    public List<DispatcherQueue> searchAll(Destination destination) {
        String path = destination.path();
        if (!path.endsWith("/*")) {
            DispatcherQueue queue = get(destination);
            return queue == null ? List.of() : List.of(queue);
        }

        String prefix = path.substring(0, path.length() - 2);
        return map.entrySet()
                .stream()
                .filter(entry -> isDescendant(entry.getKey().path(), prefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public boolean exists(final Destination destination) {
        return map.containsKey(destination);
    }

    @Override
    public void remove(Destination destination) {
        map.remove(destination);
    }

    private boolean isDescendant(String path, String prefix) {
        if (prefix.isEmpty()) {
            return path.startsWith("/");
        }
        return path.startsWith(prefix + "/");
    }
}
