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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;

/**
 * FIFO queue implementation for one dispatcher destination.
 *
 * <p>Messages are dispatched in insertion order through {@link LinkedBlockingQueue}. Pausing the
 * queue blocks enqueue attempts while consumers can continue draining already queued data.</p>
 *
 * @author yungwang-o
 */
class MessageDispatcherQueue implements DispatcherQueue {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcherQueue.class);

    private final BlockingQueue<Message> queue;
    private final DestinationQueueMetadata metadata;
    private volatile Destination destination;

    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pauseCondition = pauseLock.newCondition();
    private volatile boolean manuallyPaused;
    private volatile boolean pressurePaused;

    /**
     * {@link LinkedBlockingQueue} unbounded queue.
     */
    MessageDispatcherQueue(final Destination destination) {
        this(
                destination,
                new DestinationQueueMetadata(
                        destination.path(),
                        Instant.now(),
                        DispatcherQueue.DEFAULT_MAX_PENDING_BYTES
                )
        );
    }

    MessageDispatcherQueue(final Destination destination, final long maxPendingBytes) {
        this(
                destination,
                new DestinationQueueMetadata(
                        destination.path(),
                        Instant.now(),
                        maxPendingBytes
                )
        );
    }

    MessageDispatcherQueue(final Destination destination, DestinationQueueMetadata metadata) {
        this.metadata = metadata;
        this.queue = new LinkedBlockingQueue<>();
        this.destination = destination;
    }

    @Override
    public DestinationQueueMetadata metadata() {
        return metadata;
    }

    @Override
    public Destination route() {
        return destination;
    }

    @Override
    public String getDestination() {
        return destination.path();
    }

    @Override
    public boolean equalsTo(final Destination key) {
        return destination.equals(key);
    }

    @Override
    public @Nullable Message enqueue(final Message message) {
        if(isPaused()) {
            log.info("Waiting for queue to be resumed");
            if (!awaitResume()) {
                return null;
            }
        }

        long messageSize = message.getSize();
        if (messageSize > metadata.getMaxPendingBytes()) {
            return null;
        }

        if (!metadata.tryReserve(messageSize)) {
            pauseForPressure();
            return null;
        }

        if(queue.offer(message)) {
            return message;
        }

        metadata.release(messageSize);
        return null;
    }

    @Override
    public boolean contains(Message message) {
        return queue.contains(message);
    }

    @Override
    public @Nullable Message peek() {
        return queue.peek();
    }

    @Override
    public boolean hasNext() {
        return queue.iterator().hasNext();
    }

    @Override
    public Message next() {
        return queue.iterator().next();
    }

    @Override
    public void pause() {
        pauseLock.lock();
        try {
            manuallyPaused = true;
            metadata.paused(true);
            log.info("Pausing queue");
        } finally {
            pauseLock.unlock();
        }
    }

    @Override
    public void resume() {
        pauseLock.lock();
        try {
            manuallyPaused = false;
            updatePauseState();
        } finally {
            pauseLock.unlock();
        }
    }

    @Override
    public boolean isPaused() {
        return manuallyPaused || pressurePaused;
    }

    @Override
    public List<Message> snapshot() {
        return queue.stream().toList();
    }

    @Override
    public Message dispatch() throws InterruptedException {
        Message message = queue.take();
        metadata.release(message.getSize());
        resumeAfterPressure();
        return message;
    }

    @Override
    public @Nullable Message dispatch(long timeout, TimeUnit unit) throws InterruptedException {
        Message message = queue.poll(timeout, unit);
        if (message != null) {
            metadata.release(message.getSize());
            resumeAfterPressure();
        }
        return message;
    }

    @Override
    public void updateRoutingKey(final Destination key) {

        synchronized (this) {
            this.destination = key;
            metadata.destination(key.path());
        }
    }

    @Override
    public void remove(Message message) {
        if(!queue.remove(message)) {
            throw new IllegalStateException("Message not found");
        }
        metadata.release(message.getSize());
        resumeAfterPressure();
    }

    @Override
    public long getPendingBytes() {
        return metadata.getPendingBytes();
    }

    @Override
    public long getMaxPendingBytes() {
        return metadata.getMaxPendingBytes();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public int getSize() {
        return size();
    }

    @Override
    public void clear() {
        List<Message> removed = new ArrayList<>();
        queue.drainTo(removed);
        if (removed.isEmpty()) {
            return;
        }

        long releasedBytes = removed.stream().mapToLong(Message::getSize).sum();
        metadata.release(releasedBytes);
        resumeAfterPressure();
    }

    private boolean awaitResume() {
        pauseLock.lock();
        try {
            while (isPaused()) {
                pauseCondition.await();
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pauseLock.unlock();
        }
    }

    private void pauseForPressure() {
        pauseLock.lock();
        try {
            if (!pressurePaused) {
                pressurePaused = true;
                metadata.paused(true);
                log.info("Pausing queue due to pending bytes. destination={}", destination.path());
            }
        } finally {
            pauseLock.unlock();
        }
    }

    private void resumeAfterPressure() {
        if (!pressurePaused || metadata.isSaturated()) {
            return;
        }

        pauseLock.lock();
        try {
            if (pressurePaused && !metadata.isSaturated()) {
                pressurePaused = false;
                updatePauseState();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    private void updatePauseState() {
        boolean paused = isPaused();
        metadata.paused(paused);
        if (!paused) {
            log.info("Resuming queue. destination={}", destination.path());
            pauseCondition.signalAll();
        }
    }
}
