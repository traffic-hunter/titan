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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.concurrent.ThreadSafe;

/**
 * @author yungwang-o
 */
@ThreadSafe
public final class TrieImpl<T> implements Trie<T> {

    private final Node<T> root = new Node<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Lock rLock = lock.readLock();
    private final Lock wLock = lock.writeLock();

    @Override
    public T insert(final String word, final T value) {
        String[] split = word.split("/");

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
        String[] split = word.split("/");

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

        String[] split = prefix.split("/");

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

            return searchAll(current);
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public boolean startsWith(final String prefix) {
        String[] split = prefix.split("/");

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
    public void remove(final String word) {
        String[] split = word.split("/");

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
