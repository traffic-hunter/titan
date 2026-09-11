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

    public @Nullable StompServerSubscription unregister(StompClientChannel connection, String subscriptionId) {
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

    public List<StompServerSubscription> unregisterAll(StompClientChannel connection) {
        ConcurrentMap<String, StompServerSubscription> removed = subscriptions.remove(connection.session());
        if (removed == null) {
            return List.of();
        }
        return List.copyOf(removed.values());
    }

    public @Nullable StompServerSubscription find(StompClientChannel connection, String subscriptionId) {
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

    /** Subscriptions on the destination inside the given group only. */
    public List<StompServerSubscription> findByDestination(String group, Destination destination) {
        return values().stream()
                .filter(subscription -> subscription.getGroup().equals(group)
                        && subscription.destination().equals(destination))
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
