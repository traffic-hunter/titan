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
package org.traffichunter.titan.servicediscovery.registery;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.servicediscovery.RoutingKey;
import org.traffichunter.titan.servicediscovery.RoutingTable;

/**
 * @author yungwang-o
 */
final class GuavaServiceRegistry implements ServiceRegistry {

    private final Cache<RoutingKey, RoutingTable> tables;

    public GuavaServiceRegistry(final int capacity) {
        this.tables = CacheBuilder.newBuilder()
                .initialCapacity(capacity)
                .maximumSize(capacity)
                .concurrencyLevel(Runtime.getRuntime().availableProcessors())
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public void register(final RoutingTable routingTable) {
        if(routingTable == null) {
            throw new IllegalStateException("Routing table is null");
        }

        tables.put(routingTable.routingKey(), routingTable);
    }

    @Override
    public void unRegister(final RoutingKey routingKey) {
        if(routingKey == null) {
            throw new IllegalStateException("Routing table is null");
        }

        tables.invalidate(routingKey);
    }

    @Override
    public boolean isRegistered(final RoutingKey routingKey) {
        if(routingKey == null) {
            return false;
        }

        return tables.getIfPresent(routingKey) != null;
    }

    @Override
    public RoutingTable getService(final RoutingKey routingKey) {
        if(routingKey == null) {
            return null;
        }

        return tables.getIfPresent(routingKey);
    }

    @Override
    public List<RoutingTable> getServices() {
        return tables.asMap().values().stream()
                .toList();
    }

    @Override
    public boolean isEmpty() {
        return tables.asMap().isEmpty();
    }

    @Override
    public void clear() {
        tables.invalidateAll();
    }
}
