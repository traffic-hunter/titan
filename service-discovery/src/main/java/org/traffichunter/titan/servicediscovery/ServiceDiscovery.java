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
package org.traffichunter.titan.servicediscovery;

import java.util.List;
import org.traffichunter.titan.bootstrap.event.EventBusHolder;
import org.traffichunter.titan.servicediscovery.registery.ServiceRegistry;
import org.traffichunter.titan.servicediscovery.registery.ServiceRegistry.Struct;

/**
 * @author yungwang-o
 */
public class ServiceDiscovery {

    private final ServiceRegistry serviceRegistry;

    private final EventBusHolder eventBusHolder = EventBusHolder.INSTANCE;

    public ServiceDiscovery(final Struct struct) {

        this.serviceRegistry = switch (struct) {
            case CACHE -> ServiceRegistry.cache();
            case MAP -> ServiceRegistry.map();
            case null -> throw new IllegalStateException("Unexpected value: " + struct);
        };
    }

    public void discover(final RoutingTable routingTable) {
        serviceRegistry.register(routingTable);
    }

    public void unDiscover(final RoutingKey routingKey) {
        serviceRegistry.unRegister(routingKey);
    }

    private List<RoutingTable> getService() {
        return serviceRegistry.getServices();
    }

    private void warmUp() {

    }
}
