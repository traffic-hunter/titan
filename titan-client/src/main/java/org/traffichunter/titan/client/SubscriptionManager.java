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
