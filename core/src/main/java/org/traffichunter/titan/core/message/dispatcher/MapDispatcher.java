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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Hash-map dispatcher implementation.
 *
 * <p>This implementation is useful when destinations should be matched exactly and prefix
 * behavior is unnecessary.</p>
 *
 * @author yungwang-o
 */
@Getter
@Slf4j
public class MapDispatcher implements Dispatcher {

    private final Map<Destination, DispatcherQueue> map;

    public MapDispatcher(final int initialCapacity) {
        this(new ConcurrentHashMap<>(initialCapacity));
    }

    public MapDispatcher(final Map<Destination, DispatcherQueue> map) {
        this.map = map;
    }

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        return map.get(destination);
    }

    @Override
    public DispatcherQueue getOrPut(final Destination destination) {
        return map.putIfAbsent(destination, DispatcherQueue.create(destination));
    }

    @Override
    public boolean exists(final Destination destination) {
        return map.containsKey(destination);
    }

    @Override
    public void remove(Destination destination) {
        map.remove(destination);
    }
}
