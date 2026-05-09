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
