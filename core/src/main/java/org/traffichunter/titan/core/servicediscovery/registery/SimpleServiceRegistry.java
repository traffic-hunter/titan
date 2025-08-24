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
package org.traffichunter.titan.core.servicediscovery.registery;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.traffichunter.titan.core.servicediscovery.Subscription;
import org.traffichunter.titan.core.util.RoutingKey;

/**
 * @author yungwang-o
 */
final class SimpleServiceRegistry implements ServiceRegistry {

    private final Map<RoutingKey, Subscription> tables;

    public SimpleServiceRegistry(final int maxConnections) {
        this.tables = new ConcurrentHashMap<>(maxConnections);
    }

    @Override
    public void register(final RoutingKey key, final Subscription subscription) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(subscription, "routingTable");

        tables.put(key, subscription);
    }

    @Override
    public void unRegister(final RoutingKey key) {
        Objects.requireNonNull(key, "key");

        tables.remove(key);
    }

    @Override
    public boolean isRegistered(final RoutingKey key) {
        Objects.requireNonNull(key, "key");

        return tables.containsKey(key);
    }

    @Override
    public Subscription getService(final RoutingKey key) {
        Objects.requireNonNull(key, "key");

        return tables.get(key);
    }

    @Override
    public List<RoutingKey> keys() {
        return tables.keySet()
                .stream()
                .toList();
    }

    @Override
    public List<Subscription> getServices() {
        return tables.values()
                .stream()
                .toList();
    }

    @Override
    public boolean isEmpty() {
        return tables.isEmpty();
    }

    @Override
    public void clear() {
        tables.clear();
    }
}
