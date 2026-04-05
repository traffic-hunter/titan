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
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.util.Destination;

/**
 * @author yungwang-o
 */
public interface Dispatcher {

    static Dispatcher getDefault() {
        return new TrieDispatcher();
    }

    /**
     * @param key routing key
     * @return null
     */
    @Nullable DispatcherQueue find(Destination key);

    boolean exists(Destination key);

    /**
     * @param key routing key
     */
    default void subscribe(Destination key) {
        subscribe(key, DispatcherQueue.create(key));
    }

    void subscribe(Destination key, DispatcherQueue queue);

    void unsubscribe(Destination key);

    void update(Destination originKey, Destination updateKey);

    boolean dispatch(Destination key, NetChannel channel);
}
