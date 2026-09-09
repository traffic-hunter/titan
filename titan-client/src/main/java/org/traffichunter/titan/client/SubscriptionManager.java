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
package org.traffichunter.titan.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

/**
 * Thread-safe registry of logical subscriptions for one client.
 *
 * <p>Transport subscription registries belong to one physical connection and disappear when
 * that connection is replaced. This registry stores logical subscriptions separately.
 * Snapshots are shallow copies; callers do not receive the mutable registry itself.</p>
 *
 * @author yun
 */
public final class SubscriptionManager {

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    /** Creates an empty logical subscription registry. */
    public SubscriptionManager() {
    }

    /**
     * Adds or replaces a subscription using its identifier as the key.
     *
     * @param subscription logical subscription metadata
     */
    public void add(Subscription subscription) {
        subscriptions.put(subscription.id(), subscription);
    }

    /**
     * Returns a subscription by identifier when it is currently registered.
     *
     * @param subscriptionId subscription identifier
     * @return matching subscription, or {@code null} when absent
     */
    public @Nullable Subscription get(String subscriptionId) {
        return subscriptions.get(subscriptionId);
    }

    /**
     * Returns a stable snapshot suitable for reconnect restoration.
     *
     * @return immutable list snapshot of current subscriptions
     */
    public List<Subscription> subscriptions() {
        return List.copyOf(subscriptions.values());
    }

    /**
     * Removes the subscription when present.
     *
     * @param subscriptionId subscription identifier
     */
    public void remove(String subscriptionId) {
        subscriptions.remove(subscriptionId);
    }

    /**
     * Returns whether no logical subscriptions are registered.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return subscriptions.isEmpty();
    }

    /**
     * Returns the current number of logical subscriptions.
     *
     * @return subscription count
     */
    public int size() {
        return subscriptions.size();
    }

    /** Removes all logical subscriptions during disconnect or shutdown. */
    public void clear() {
        subscriptions.clear();
    }
}
