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
package org.traffichunter.titan.dispatch;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.concurrent.Pausable;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbean;
import org.traffichunter.titan.core.util.management.DispatcherQueueMbeans;

/**
 * Queue of messages for one destination.
 *
 * <p>The queue is the handoff point between producers and destination consumers. It supports
 * pausing producers, inspecting queued pressure, and blocking dispatch for consumers.</p>
 *
 * @author yungwang-o
 */
public interface DispatcherQueue extends Pausable, Iterator<Message>, DispatcherQueueMbean {

    long DEFAULT_MAX_PENDING_BYTES = Long.MAX_VALUE;

    static DispatcherQueue create(Destination key) {
        return create(key, DEFAULT_MAX_PENDING_BYTES);
    }

    static DispatcherQueue create(Destination key, long maxPendingBytes) {
        return create(
                key,
                maxPendingBytes,
                DestinationQueueMetadata.defaultResumePendingBytes(maxPendingBytes)
        );
    }

    static DispatcherQueue create(Destination key, long maxPendingBytes, long resumePendingBytes) {
        DispatcherQueue queue = new MessageDispatcherQueue(
                key,
                new DestinationQueueMetadata(
                        key.path(),
                        Instant.now(),
                        maxPendingBytes,
                        resumePendingBytes
                )
        );
        DispatcherQueueMbeans.register(queue);
        return queue;
    }

    DestinationQueueMetadata metadata();

    /**
     * Destination served by this queue.
     */
    Destination route();

    boolean equalsTo(Destination key);

    /**
     * Enqueues a message, returning {@code null} when the queue refuses it.
     */
    @CanIgnoreReturnValue
    @Nullable Message enqueue(Message message);

    boolean contains(Message message);

    @Nullable Message peek();

    /**
     * Returns a snapshot of queued messages for pressure inspection.
     */
    List<Message> snapshot();

    /**
     * Blocks until a message is available.
     */
    Message dispatch() throws InterruptedException;

    /**
     * Waits for a message until the timeout expires.
     *
     * @return a message, or {@code null} when no message is available before
     * the timeout
     */
    @Nullable Message dispatch(long timeout, TimeUnit unit) throws InterruptedException;

    void remove(Message message);

    void updateRoutingKey(Destination key);

    int size();

    void clear();
}
