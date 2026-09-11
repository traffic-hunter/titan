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
package org.traffichunter.titan.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;

/**
 * Path trie keyed by {@code /}-separated segments.
 *
 * <p>Reads take no lock. Each node keeps its children in a {@link ConcurrentHashMap} and its
 * value in a volatile field, so a reader always sees a fully published node. Writes share one
 * lock, which keeps a removal that prunes empty nodes from interleaving with an insert that is
 * descending through them. A reader that races a removal may see the value or {@code null},
 * never a half-built node.</p>
 *
 * @author yungwang-o
 */
@ThreadSafe
public final class TrieImpl<T> implements Trie<T> {

    private static final String SPLITTER = "/";

    private final Node<T> root = new Node<>();

    private final Lock wLock = new ReentrantLock();

    @Override
    public T insert(final String word, final T value) {
        String[] split = word.split(SPLITTER);

        wLock.lock();
        try {
            Node<T> current = root;

            for (String str : split) {
                if (str.isEmpty()) {
                    continue; // Skip empty strings from leading /
                }

                current = current.children.computeIfAbsent(str, k -> new Node<>());
            }
            return current.value = value;
        } finally {
            wLock.unlock();
        }
    }

    @Override
    public @Nullable T get(final String word) {
        Node<T> current = root;

        for (String str : word.split(SPLITTER)) {
            if (str.isEmpty()) {
                continue; // Skip empty strings from leading /
            }

            current = current.children.get(str);

            if (current == null) {
                return null;
            }
        }
        return current.value;
    }

    @Override
    public List<T> searchAll() {
        return searchAll("/*");
    }

    @Override
    public List<T> searchAll(final String prefix) {
        if (!prefix.startsWith("/")) {
            throw new IllegalArgumentException("prefix must be a path ending with '/*'");
        }

        String[] split = prefix.split(SPLITTER);

        validateWildcard(split);

        Node<T> current = root;

        for (int i = 0; i < split.length - 1; i++) {
            if (split[i].isEmpty()) {
                continue; // Skip empty strings from leading /
            }

            current = current.children.get(split[i]);

            if (current == null) {
                return List.of();
            }
        }

        return searchChildren(current);
    }

    @Override
    public boolean startsWith(final String prefix) {
        Node<T> current = root;

        for (String str : prefix.split(SPLITTER)) {
            if (str.isEmpty()) {
                continue; // Skip empty strings from leading /
            }

            current = current.children.get(str);

            if (current == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves without a lock first. Routing calls this for every message and the value
     * almost always exists, so only a miss pays for the lock. The miss path checks again
     * under the lock because another thread may have filled it in.
     */
    @Override
    public T computeIfAbsent(String word, Function<? super String, ? extends T> mappingFunction) {
        T existing = get(word);
        if (existing != null) {
            return existing;
        }

        wLock.lock();
        try {
            Node<T> current = root;
            for (String str : word.split(SPLITTER)) {
                if (str.isEmpty()) {
                    continue;
                }
                current = current.children.computeIfAbsent(str, key -> new Node<>());
            }

            if (current.value == null) {
                current.value = mappingFunction.apply(word);
            }

            return current.value;
        } finally {
            wLock.unlock();
        }
    }

    @Override
    public @Nullable T putIfAbsent(String word, T value) {
        wLock.lock();
        try {
            Node<T> current = root;
            for (String str : word.split(SPLITTER)) {
                if (str.isEmpty()) {
                    continue;
                }
                current = current.children.computeIfAbsent(str, key -> new Node<>());
            }

            T previous = current.value;
            if (previous == null) {
                current.value = value;
            }
            return previous;
        } finally {
            wLock.unlock();
        }
    }

    @Override
    public @Nullable T remove(final String word) {
        String[] split = word.split(SPLITTER);

        wLock.lock();
        try {
            return remove(root, split, 0);
        } finally {
            wLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        return root.children.isEmpty();
    }

    private List<T> searchChildren(final Node<T> node) {
        List<T> list = new ArrayList<>();

        for (Node<T> child : node.children.values()) {
            tour(child, list);
        }

        return list;
    }

    private void validateWildcard(String[] parts) {
        boolean foundWildcard = false;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (part.isBlank()) {
                continue;
            }

            if (part.contains("*")) {
                if (!"*".equals(part) || i != parts.length - 1) {
                    throw new IllegalArgumentException("prefix must end with '/*'");
                }
                foundWildcard = true;
            }
        }

        if (!foundWildcard) {
            throw new IllegalArgumentException("prefix must end with '/*'");
        }
    }

    private void tour(final Node<T> node, final List<T> list) {
        if(node.value != null) {
            list.add(node.value);
        }

        for(Node<T> child : node.children.values()) {
            tour(child, list);
        }
    }

    /** Removes the value at the end of {@code parts} and prunes nodes left empty on the way back up. */
    private @Nullable T remove(final Node<T> node, final String[] parts, int idx) {
        // Skip empty strings from leading /
        while (idx < parts.length && parts[idx].isEmpty()) {
            idx++;
        }

        if (idx == parts.length) {
            T value = node.value;
            node.value = null;
            return value;
        }

        String part = parts[idx];
        Node<T> child = node.children.get(part);
        if (child == null) {
            return null;
        }

        T removed = remove(child, parts, idx + 1);
        if (removed != null && child.value == null && child.children.isEmpty()) {
            node.children.remove(part);
        }

        return removed;
    }

    static class Node<T> {

        final Map<String, Node<T>> children = new ConcurrentHashMap<>();
        volatile @Nullable T value;
    }
}
