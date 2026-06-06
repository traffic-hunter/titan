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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;

/**
 * Priority queue implementation for one dispatcher destination.
 *
 * <p>Messages are ordered by their natural ordering in {@link PriorityBlockingQueue}. Pausing
 * the queue blocks enqueue attempts while consumers can continue draining already queued data.</p>
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
     * {@link PriorityBlockingQueue} default capacity 11
     */
    MessageDispatcherQueue(final Destination destination) {
        this(destination, 11);
    }

    MessageDispatcherQueue(final Destination destination, final int capacity) {
        this.capacity = capacity;
        this.queue = new PriorityBlockingQueue<>(capacity);
        this.destination = destination;
    }

    @Override
    public Destination route() {
        return destination;
    }

    @Override
    public boolean equalsTo(final Destination key) {
        return destination.equals(key);
    }

    @Override
    public @Nullable Message enqueue(final Message message) {
        if(isPaused) {
            log.info("Waiting for queue to be resumed");
            wait0();
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
    public List<Message> pressure() {
        List<Message> messages = new ArrayList<>();
        queue.drainTo(messages);

        return messages;
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
    public int size() {
        return queue.size();
    }

    @Override
    public void clear() {
        queue.clear();
    }

    private void wait0() {
        pauseLock.lock();
        try {
            while (isPaused) {
                pauseCondition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pauseLock.unlock();
        }
    }
}
