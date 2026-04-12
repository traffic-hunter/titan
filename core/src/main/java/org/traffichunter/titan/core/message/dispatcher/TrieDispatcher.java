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
package org.traffichunter.titan.core.message.dispatcher;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Trie;
import org.traffichunter.titan.core.util.TrieImpl;

/**
 * @author yungwang-o
 */
public class TrieDispatcher implements Dispatcher {

    private final Trie<DispatcherQueue> trie = new TrieImpl<>();

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        return trie.get(destination.path());
    }

    /**
     * @param destination routingKey
     * @return null
     */
    @Override
    public @Nullable DispatcherQueue getOrPut(final Destination destination) {
        DispatcherQueue v = trie.get(destination.path());
        if (v == null) {
            v = trie.insert(destination.path(), DispatcherQueue.create(destination));
        }

        return v;
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
        trie.remove(destination.path());
    }
}
