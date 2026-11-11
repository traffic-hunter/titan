/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.codec.stomp;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.util.Destination;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Server-side subscription registry keyed by destination group and destination.
 *
 * <p>Looking up the subscribers of one destination does not scan other destinations. Lookups by
 * session scan the destinations instead, which stays cheap while there are few destinations.</p>
 *
 * @author yun
 */
public final class StompServerSubscriptions {

    // (group, destination) -> subscriptions on it, keyed by session id and subscription id.
    // Subscription ids are unique only within a session.
    private final ConcurrentMap<Route, ConcurrentMap<Key, StompServerSubscription>> subscriptions =
            new ConcurrentHashMap<>();

    public boolean register(StompServerSubscription subscription) {
        // One session's frames run on its own event loop, so this check and the insert below
        // do not race with another SUBSCRIBE of the same session.
        if (find(subscription.getConnection(), subscription.id()) != null) {
            return false;
        }

        subscriptions.compute(Route.of(subscription), (route, routeSubscriptions) -> {
            ConcurrentMap<Key, StompServerSubscription> target =
                    routeSubscriptions == null ? new ConcurrentHashMap<>() : routeSubscriptions;
            target.put(Key.of(subscription), subscription);
            return target;
        });
        return true;
    }

    public @Nullable StompServerSubscription unregister(StompClientChannel connection, String subscriptionId) {
        StompServerSubscription subscription = find(connection, subscriptionId);
        if (subscription == null) {
            return null;
        }

        subscriptions.computeIfPresent(Route.of(subscription), (route, routeSubscriptions) -> {
            routeSubscriptions.remove(Key.of(subscription), subscription);
            return routeSubscriptions.isEmpty() ? null : routeSubscriptions;
        });
        return subscription;
    }

    /** Removes every subscription of the connection's session and returns whether there was any. */
    public boolean unregisterAll(StompClientChannel connection) {
        String sessionId = connection.session();
        boolean removed = false;
        for (Map.Entry<Route, ConcurrentMap<Key, StompServerSubscription>> entry : subscriptions.entrySet()) {
            if (entry.getValue().keySet().removeIf(key -> key.sessionId().equals(sessionId))) {
                removed = true;
                subscriptions.computeIfPresent(entry.getKey(), (route, routeSubscriptions) ->
                        routeSubscriptions.isEmpty() ? null : routeSubscriptions);
            }
        }
        return removed;
    }

    public @Nullable StompServerSubscription find(StompClientChannel connection, String subscriptionId) {
        Key key = new Key(connection.session(), subscriptionId);
        for (ConcurrentMap<Key, StompServerSubscription> routeSubscriptions : subscriptions.values()) {
            StompServerSubscription subscription = routeSubscriptions.get(key);
            if (subscription != null) {
                return subscription;
            }
        }
        return null;
    }

    public List<StompServerSubscription> findByDestination() {
        return values();
    }

    /** Subscriptions on the destination inside the given group only. */
    public List<StompServerSubscription> findByDestination(String group, Destination destination) {
        ConcurrentMap<Key, StompServerSubscription> routeSubscriptions =
                subscriptions.get(new Route(group, destination));
        return routeSubscriptions == null ? List.of() : List.copyOf(routeSubscriptions.values());
    }

    public List<StompServerSubscription> values() {
        return subscriptions.values().stream()
                .flatMap(routeSubscriptions -> routeSubscriptions.values().stream())
                .toList();
    }

    public int size() {
        return subscriptions.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    private record Route(String group, Destination destination) {

        static Route of(StompServerSubscription subscription) {
            return new Route(subscription.getGroup(), subscription.destination());
        }
    }

    private record Key(String sessionId, String subId) {

        static Key of(StompServerSubscription subscription) {
            return new Key(subscription.getConnection().session(), subscription.id());
        }
    }
}
