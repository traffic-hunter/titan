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
package org.traffichunter.titan.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

            Node<T> child = current.children.get(str);
            if (child == null) {
                return null;
            }
            current = child;
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

            Node<T> child = current.children.get(split[i]);
            if (child == null) {
                return List.of();
            }
            current = child;
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

            Node<T> child = current.children.get(str);
            if (child == null) {
                return false;
            }
            current = child;
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

            T value = current.value;
            if (value != null) {
                return value;
            }

            T mapped = Objects.requireNonNull(
                    mappingFunction.apply(word),
                    "mappingFunction returned null"
            );
            current.value = mapped;
            return mapped;
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
            return remove(root, split, 0, null);
        } finally {
            wLock.unlock();
        }
    }

    @Override
    public boolean remove(final String word, final T expected) {
        Objects.requireNonNull(expected, "expected");
        String[] split = word.split(SPLITTER);

        wLock.lock();
        try {
            return remove(root, split, 0, expected) != null;
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
        T value = node.value;
        if (value != null) {
            list.add(value);
        }

        for(Node<T> child : node.children.values()) {
            tour(child, list);
        }
    }

    /**
     * Removes the value at the end of {@code parts} and prunes nodes left empty on the way back
     * up. A non-null {@code expected} limits the removal to that instance.
     */
    private @Nullable T remove(
            final Node<T> node,
            final String[] parts,
            int idx,
            final @Nullable T expected
    ) {
        // Skip empty strings from leading /
        while (idx < parts.length && parts[idx].isEmpty()) {
            idx++;
        }

        if (idx == parts.length) {
            T value = node.value;
            if (value == null || (expected != null && value != expected)) {
                return null;
            }
            node.value = null;
            return value;
        }

        String part = parts[idx];
        Node<T> child = node.children.get(part);
        if (child == null) {
            return null;
        }

        T removed = remove(child, parts, idx + 1, expected);
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
