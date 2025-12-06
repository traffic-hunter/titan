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
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    public void insert(final String word, final T value) {
        String[] split = word.split("\\.");

        wLock.lock();
        try {
            Node<T> current = root;

            for (String str : split) {
                current = current.children.computeIfAbsent(str, k -> new Node<>());
            }
            current.value = value;
        } finally {
            wLock.unlock();
        }
    }

    @Override
    public Optional<T> search(final String word) {
        String[] split = word.split("\\.");

        rLock.lock();
        try {
            Node<T> current = root;

            for (String str : split) {
                current = current.children.get(str);

                if (current == null) {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(current.value);
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public List<T> searchAll() {
        return searchAll("*");
    }

    @Override
    public List<T> searchAll(final String prefix) {
        if(!prefix.endsWith("*")) {
            throw new IllegalArgumentException("prefix must end with '*'");
        }

        String[] split = prefix.split("\\.");

        rLock.lock();
        try {
            Node<T> current = root;

            for (int i = 0; i < split.length - 1; i++) {
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
        String[] split = prefix.split("\\.");
        Node<T> current = root;

        for(String str : split) {
            current = current.children.get(str);

            if (current == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void remove(final String word) {
        String[] split = word.split("\\.");

        wLock.lock();
        try {
            if(remove(root, split, 0)) {
                throw new IllegalStateException("No such word: " + word);
            }
        } finally {
            wLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        return root.value == null;
    }

    private List<T> searchAll(final Node<T> node) {
        List<T> list = new ArrayList<>();

        tour(node, list);

        return list;
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
        if(idx == parts.length) {
            if (node.value == null) {
                return false;
            }
            node.value = null;
            return node.children.isEmpty();
        }

        String part = parts[idx];
        Node<T> child = node.children.get(part);

        if (child == null) {
            return false;
        }

        boolean shouldDeleteChild = remove(child, parts, idx + 1);

        if (shouldDeleteChild) {
            node.children.remove(part);
        }

        return node.value == null && node.children.isEmpty() && node != root;
    }

    static class Node<T> {

        final Map<String, Node<T>> children = new HashMap<>();
        T value;

        Node() {
            this(null);
        }

        Node(final T value) {
            this.value = value;
        }
    }
}
