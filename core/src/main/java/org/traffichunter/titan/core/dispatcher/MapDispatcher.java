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
package org.traffichunter.titan.core.dispatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.servicediscovery.RoutingKey;

/**
 * @author yungwang-o
 */
@Getter
@Slf4j
class MapDispatcher implements Dispatcher {

    private final Map<RoutingKey, DispatcherQueue> map;

    public MapDispatcher(final int initialCapacity) {
        this.map = new ConcurrentHashMap<>(initialCapacity);
    }

    @Override
    public DispatcherQueue find(final RoutingKey key) {
        return map.get(key);
    }

    @Override
    public void insert(final RoutingKey key, final DispatcherQueue queue) {
        if(map.containsKey(key)) {
            log.error("Duplicate key: {}", key);
        }

        map.put(key, queue);
    }

    @Override
    public void remove(final RoutingKey key) {
        map.remove(key);
    }

    @Override
    public void update(final RoutingKey originKey, final RoutingKey updateKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DispatcherQueue> findAll(final RoutingKey key) {
        return map.values()
                .stream()
                .filter(val -> val.route().equals(key))
                .toList();
    }
}
