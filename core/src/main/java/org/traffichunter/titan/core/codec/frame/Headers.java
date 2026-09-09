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
package org.traffichunter.titan.core.codec.frame;

import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Base type for frame header collections.
 *
 * <p>The generic {@code H} type lets implementations return their concrete header
 * type from fluent or accessor methods.</p>
 *
 * @author yungwang-o
 */
public abstract class Headers<K, V, H extends Headers<K, V, H>> {

    protected final Map<K, V> map;

    protected Headers(final Map<K, V> map) {
        this.map = map;
    }

    /**
     * Stores a header value for the given key.
     */
    public abstract void put(K key, V value);

    /**
     * Stores a header value only when the key is absent.
     */
    public abstract void putIfAbsent(K key, V value);

    /**
     * Returns the value for the key, or the supplied default when absent.
     */
    public abstract V getOrDefault(K key, V defaultValue);

    /**
     * Returns the value for the key, or {@code null} when absent.
     */
    public abstract @Nullable V get(K key);

    /**
     * Returns whether this collection contains the key.
     */
    public abstract boolean containsKey(K key);

    /**
     * Returns all header keys.
     */
    public abstract Set<K> keySet();

    /**
     * Returns all header entries.
     */
    public abstract Set<Map.Entry<K, V>> entrySet();

    /**
     * Returns an iterator over header entries.
     */
    public abstract Iterator<Map.Entry<K, V>> iterator();

    /**
     * Returns this header collection as its concrete type.
     */
    public abstract H getHeader();
}
