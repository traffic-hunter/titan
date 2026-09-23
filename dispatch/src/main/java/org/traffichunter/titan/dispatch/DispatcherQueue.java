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
 * <p>The queue is the handoff point between producers and destination consumers. A manual pause
 * stops both sides of that handoff, while an automatic pressure pause stops producers and lets
 * consumers continue draining queued messages.</p>
 *
 * <p>A queue that leaves its dispatcher is closed and refuses further messages, so a producer
 * holding a stale reference fails instead of writing into a queue nothing drains.</p>
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
        return create(key, maxPendingBytes, resumePendingBytes, DEFAULT_GROUP);
    }

    /** Creates a queue that belongs to the named group. */
    static DispatcherQueue create(Destination key, long maxPendingBytes, long resumePendingBytes, String group) {
        DispatcherQueue queue = new MessageDispatcherQueue(
                key,
                new DestinationQueueMetadata(
                        key.path(),
                        Instant.now(),
                        maxPendingBytes,
                        resumePendingBytes
                ),
                group
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
     * Blocks until a message is available and hands it out.
     *
     * <p>The message leaves the waiting line but its bytes stay reserved until
     * {@link #complete(Message)}, so pending bytes cover the message being delivered as well
     * as the ones behind it.</p>
     */
    Message dispatch() throws InterruptedException;

    /**
     * Waits for a message until the timeout expires, then hands it out like {@link #dispatch()}.
     *
     * @return a message, or {@code null} when no message is available before
     * the timeout
     */
    @Nullable Message dispatch(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Returns the bytes of a dispatched message once its delivery is over. Call it exactly once
     * per dispatched message. A closed queue still returns the bytes.
     */
    void complete(Message message);

    void remove(Message message);

    /**
     * Refuses further messages.
     *
     * <p>Called once the queue has left its dispatcher. {@link #enqueue(Message)} then returns
     * {@code null} and any producer waiting on a pause wakes up and sees the same answer. A
     * message accepted just before the close is still queued, so a caller that must not lose
     * one checks {@link #size()} afterwards. Closing is one way.</p>
     */
    void close();

    /** {@code true} once the queue has been closed. */
    boolean isClosed();

    int size();

    void clear();
}
