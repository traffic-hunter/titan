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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.traffichunter.titan.servicediscovery.RoutingKey;
import org.traffichunter.titan.servicediscovery.RoutingTable;

/**
 * @author yungwang-o
 */
final class SimpleServiceRegistry implements ServiceRegistry {

    private final Map<RoutingKey, RoutingTable> tables = new ConcurrentHashMap<>();

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
            throw new IllegalStateException("Routing key is null");
        }

        tables.remove(routingKey);
    }

    @Override
    public boolean isRegistered(final RoutingKey routingKey) {
        if(routingKey == null) {
           return false;
        }

        return tables.containsKey(routingKey);
    }

    @Override
    public RoutingTable getService(final RoutingKey routingKey) {
        if(routingKey == null) {
            return null;
        }

        return tables.get(routingKey);
    }

    @Override
    public List<RoutingTable> getServices() {
        return tables.values().stream()
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
