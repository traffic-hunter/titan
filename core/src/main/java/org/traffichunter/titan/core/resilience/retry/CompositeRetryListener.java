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
package org.traffichunter.titan.core.resilience.retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mutable retry listener chain that forwards events in registration order.
 *
 * <p>Listeners can be added while retry operations are active. Duplicate listener
 * instances are ignored.</p>
 *
 * @author yun
 */
public final class CompositeRetryListener implements RetryListener {

    private final CopyOnWriteArrayList<RetryListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a listener to the end of this chain.
     *
     * @param listener listener to add
     * @return this listener chain
     */
    public CompositeRetryListener add(RetryListener listener) {
        listeners.addIfAbsent(listener);
        return this;
    }

    /**
     * Adds listeners to the end of this chain in declaration order.
     *
     * @param listeners listeners to add
     * @return this listener chain
     */
    public CompositeRetryListener addAll(RetryListener... listeners) {
        this.listeners.addAllAbsent(List.of(listeners));
        return this;
    }

    /**
     * Adds a listener at the specified index unless it is already registered.
     *
     * @param listener listener to add
     * @param index insertion index
     * @return this listener chain
     */
    public synchronized CompositeRetryListener add(RetryListener listener, int index) {
        if (!listeners.contains(listener)) {
            listeners.add(index, listener);
        }
        return this;
    }

    /**
     * Removes a listener from this chain.
     *
     * @param listener listener to remove
     * @return {@code true} when the listener was registered
     */
    public boolean remove(RetryListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Removes listeners assignable to the supplied type.
     *
     * @param listenerClass listener type to remove
     * @return this listener chain
     */
    public CompositeRetryListener remove(Class<? extends RetryListener> listenerClass) {
        listeners.removeIf(listener -> listenerClass.isAssignableFrom(listener.getClass()));
        return this;
    }

    /**
     * Removes every listener from this chain.
     *
     * @return this listener chain
     */
    public CompositeRetryListener clear() {
        listeners.clear();
        return this;
    }

    /**
     * Returns an immutable snapshot of the registered listeners.
     *
     * @return registered listeners
     */
    public List<RetryListener> listeners() {
        return List.copyOf(listeners);
    }

    @Override
    public void onRetry(int attempt, Duration delay) {
        listeners.forEach(listener -> listener.onRetry(attempt, delay));
    }

    @Override
    public void onRetryFailed(int attempt, Throwable cause) {
        listeners.forEach(listener -> listener.onRetryFailed(attempt, cause));
    }

}
