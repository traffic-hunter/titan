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
package org.traffichunter.titan.core.codec;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author yungwang-o
 */
public abstract class Headers<K, V, H extends Headers<K, V, H>> {

    protected final Map<K, V> map;

    protected Headers(final Map<K, V> map) {
        this.map = map;
    }

    public abstract void put(K key, V value);

    public abstract void putIfAbsent(K key, V value);

    public abstract V get(K key);

    public abstract boolean containsKey(K key);

    public abstract Set<K> keySet();

    public abstract Set<Map.Entry<K, V>> entrySet();

    public abstract Iterator<Map.Entry<K, V>> iterator();

    public abstract H getHeader();
}
