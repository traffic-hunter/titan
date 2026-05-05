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
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.util.Destination;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Server-side subscription registry scoped by STOMP session.
 *
 * @author yun
 */
public final class StompServerSubscriptions {

    private final ConcurrentMap<String, ConcurrentMap<String, StompServerSubscription>> subscriptions =
            new ConcurrentHashMap<>();

    public boolean register(StompServerSubscription subscription) {
        String sessionId = subscription.getConnection().session();
        return subscriptions
                .computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(subscription.id(), subscription) == null;
    }

    public @Nullable StompServerSubscription unregister(StompClientConnection connection, String subscriptionId) {
        ConcurrentMap<String, StompServerSubscription> sessionSubscriptions =
                subscriptions.get(connection.session());
        if (sessionSubscriptions == null) {
            return null;
        }

        StompServerSubscription removed = sessionSubscriptions.remove(subscriptionId);
        if (sessionSubscriptions.isEmpty()) {
            subscriptions.remove(connection.session(), sessionSubscriptions);
        }
        return removed;
    }

    public List<StompServerSubscription> unregisterAll(StompClientConnection connection) {
        ConcurrentMap<String, StompServerSubscription> removed = subscriptions.remove(connection.session());
        if (removed == null) {
            return List.of();
        }
        return List.copyOf(removed.values());
    }

    public @Nullable StompServerSubscription find(StompClientConnection connection, String subscriptionId) {
        ConcurrentMap<String, StompServerSubscription> sessionSubscriptions =
                subscriptions.get(connection.session());
        if (sessionSubscriptions == null) {
            return null;
        }
        return sessionSubscriptions.get(subscriptionId);
    }

    public List<StompServerSubscription> findByDestination() {
        return values();
    }

    public List<StompServerSubscription> findByDestination(Destination destination) {
        return values().stream()
                .filter(subscription -> subscription.destination().equals(destination))
                .toList();
    }

    public List<StompServerSubscription> values() {
        return subscriptions.values().stream()
                .flatMap(sessionSubscriptions -> sessionSubscriptions.values().stream())
                .toList();
    }

    public int size() {
        return subscriptions.values().stream()
                .mapToInt(ConcurrentMap::size)
                .sum();
    }
}
