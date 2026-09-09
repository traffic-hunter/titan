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
