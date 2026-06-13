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
