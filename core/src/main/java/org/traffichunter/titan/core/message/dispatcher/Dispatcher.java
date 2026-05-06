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
package org.traffichunter.titan.core.message.dispatcher;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;

/**
 * Registry of destination queues.
 *
 * <p>Dispatchers do not deliver messages directly. They resolve the queue for a destination,
 * creating it when necessary, and queue consumers perform the actual dispatch from
 * {@link DispatcherQueue}.</p>
 *
 * @author yungwang-o
 */
public interface Dispatcher {

    /**
     * Returns Titan's default destination registry implementation.
     */
    static Dispatcher getDefault() {
        return new TrieDispatcher();
    }

    /**
     * Returns the queue for the destination, or {@code null} when it has not been created.
     */
    @Nullable DispatcherQueue get(Destination destination);

    /**
     * Returns the existing queue or creates one for this destination.
     */
    @CanIgnoreReturnValue
    DispatcherQueue getOrPut(Destination destination);

    boolean exists(Destination destination);

    void remove(Destination destination);
}
