/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.codec.stomp;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author yun
 */
public final class StompSubscriptions<S extends StompSubscription> {

    private final ConcurrentMap<String, S> subscriptions = new ConcurrentHashMap<>();

    public boolean register(S subscription) {
        return subscriptions.putIfAbsent(subscription.id(), subscription) == null;
    }

    public @Nullable S unregister(String id) {
        return subscriptions.remove(id);
    }

    public @Nullable S find(String id) {
        return subscriptions.get(id);
    }

    public List<S> findByDestination() {
        return subscriptions.values().stream().toList();
    }

    public List<S> findByDestination(Destination destination) {
        return subscriptions.values().stream()
                .filter(subscription -> subscription.destination().equals(destination))
                .toList();
    }

    public List<S> values() {
        return List.copyOf(subscriptions.values());
    }

    public int size() {
        return subscriptions.size();
    }
}
