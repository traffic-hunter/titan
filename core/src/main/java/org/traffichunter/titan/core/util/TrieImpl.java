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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;

/**
 * @author yungwang-o
 */
@ThreadSafe
public final class TrieImpl<T> implements Trie<T> {

    private static final String SPLITTER = "/";

    private final Node<T> root = new Node<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Lock rLock = lock.readLock();
    private final Lock wLock = lock.writeLock();

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
        String[] split = word.split(SPLITTER);

        rLock.lock();
        try {
            Node<T> current = root;

            for (String str : split) {
                if (str.isEmpty()) {
                    continue; // Skip empty strings from leading /
                }

                current = current.children.get(str);

                if (current == null) {
                    return null;
                }
            }
            return current.value;
        } finally {
            rLock.unlock();
        }
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

        rLock.lock();
        try {
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
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public boolean startsWith(final String prefix) {
        String[] split = prefix.split(SPLITTER);

        rLock.lock();
        try {
            Node<T> current = root;

            for(String str : split) {
                if (str.isEmpty()) {
                    continue; // Skip empty strings from leading /
                }

                current = current.children.get(str);

                if (current == null) {
                    return false;
                }
            }
            return true;
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public T computeIfAbsent(String word, Function<? super String, ? extends T> mappingFunction) {
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
    public void remove(final String word) {
        String[] split = word.split(SPLITTER);

        wLock.lock();
        try {
            if(!remove(root, split, 0)) {
                throw new IllegalStateException("No such word: " + word);
            }
        } finally {
            wLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        rLock.lock();
        try {
            return root.children.isEmpty();
        } finally {
            rLock.unlock();
        }
    }

    private List<T> searchAll(final Node<T> node) {
        List<T> list = new ArrayList<>();

        tour(node, list);

        return list;
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

    private boolean remove(final Node<T> node, final String[] parts, int idx) {
        // Skip empty strings from leading /
        while (idx < parts.length && parts[idx].isEmpty()) {
            idx++;
        }

        if(idx == parts.length) {
            if (node.value == null) {
                return false;
            }
            node.value = null;
            return true;
        }

        String part = parts[idx];
        Node<T> child = node.children.get(part);

        if (child == null) {
            return false;
        }

        boolean removed = remove(child, parts, idx + 1);

        if (!removed) {
            return false;
        }

        if (child.value == null && child.children.isEmpty()) {
            node.children.remove(part);
        }

        return true;
    }

    static class Node<T> {

        final Map<String, Node<T>> children = new HashMap<>();
        @Nullable T value;

        Node() {
            this(null);
        }

        Node(final @Nullable T value) {
            this.value = value;
        }
    }
}
