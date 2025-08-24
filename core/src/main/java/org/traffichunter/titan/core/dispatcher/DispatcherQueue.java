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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Iterator;
import java.util.List;
import org.traffichunter.titan.core.message.AbstractMessage;
import org.traffichunter.titan.core.servicediscovery.ServiceDiscovery;
import org.traffichunter.titan.core.util.RoutingKey;
import org.traffichunter.titan.core.util.concurrent.Pausable;
import org.traffichunter.titan.core.util.mbeans.DispatcherQueueMbean;

/**
 * @author yungwang-o
 */
public interface DispatcherQueue extends Pausable, Iterator<AbstractMessage>, DispatcherQueueMbean {

    static DispatcherQueue create(RoutingKey key) {
        return new MessageDispatcherQueue(key);
    }

    static DispatcherQueue create(RoutingKey key, int capacity) {
        return new MessageDispatcherQueue(key, capacity);
    }

    RoutingKey route();

    boolean equalsTo(RoutingKey key);

    ServiceDiscovery serviceDiscovery();

    @CanIgnoreReturnValue
    AbstractMessage enqueue(AbstractMessage message);

    AbstractMessage peek();

    List<AbstractMessage> pressure();

    AbstractMessage dispatch();

    void updateRoutingKey(RoutingKey key);

    int size();

    int capacity();

    void clear();
}
