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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
@Slf4j
class MessageDispatcherQueue implements DispatcherQueue {

    private final BlockingQueue<Message> queue;
    private final int capacity;
    private Destination destination;

    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pauseCondition = pauseLock.newCondition();
    private volatile boolean isPaused = false;

    /**
     * {@link LinkedBlockingQueue} unbounded queue.
     */
    MessageDispatcherQueue(final Destination destination) {
        this(destination, Integer.MAX_VALUE);
    }

    MessageDispatcherQueue(final Destination destination, final int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.destination = destination;
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
        if(isPaused) {
            log.info("Waiting for queue to be resumed");
            if (!awaitResume()) {
                return null;
            }
        }

        if(queue.offer(message)) {
            return message;
        }

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
            isPaused = true;
            log.info("Pausing queue");
        } finally {
            pauseLock.unlock();
        }
    }

    @Override
    public void resume() {
        pauseLock.lock();
        try {
            isPaused = false;
            log.info("Resuming queue");
            pauseCondition.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    @Override
    public boolean isPaused() {
        return isPaused;
    }

    @Override
    public List<Message> snapshot() {
        return queue.stream().toList();
    }

    @Override
    public Message dispatch() throws InterruptedException {
        return queue.take();
    }

    @Override
    public @Nullable Message dispatch(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    @Override
    public void updateRoutingKey(final Destination key) {

        synchronized (this) {
            this.destination = key;
        }
    }

    @Override
    public void remove(Message message) {
        if(!queue.remove(message)) {
            throw new IllegalStateException("Message not found");
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int getCapacity() {
        return capacity();
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
        queue.clear();
    }

    private boolean awaitResume() {
        pauseLock.lock();
        try {
            while (isPaused) {
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
}
