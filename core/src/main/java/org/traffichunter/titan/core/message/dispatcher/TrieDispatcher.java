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

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Trie;
import org.traffichunter.titan.core.util.TrieImpl;

/**
 * @author yungwang-o
 */
public class TrieDispatcher implements Dispatcher {

    private final Trie<DispatcherQueue> trie = new TrieImpl<>();

    /**
     * @param key routingKey
     * @return null
     */
    @Override
    public @Nullable DispatcherQueue find(final Destination key) {
        return trie.get(key.path());
    }

    @Override
    public boolean exists(final Destination key) {
        return trie.startsWith(key.path());
    }

    @Override
    public void insert(final Destination key, final DispatcherQueue queue) {
        trie.insert(key.path(), queue);
    }

    @Override
    public void remove(final Destination key) {
        trie.remove(key.path());
    }

    @Override
    public void update(final Destination originKey, final Destination updateKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DispatcherQueue> dispatch(final Destination key) {
        return trie.searchAll(key.path());
    }
}
