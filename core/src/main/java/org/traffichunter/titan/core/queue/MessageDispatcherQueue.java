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
package org.traffichunter.titan.core.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.message.AbstractMessage;
import org.traffichunter.titan.servicediscovery.RoutingKey;

/**
 * @author yungwang-o
 */
@Slf4j
class MessageDispatcherQueue implements DispatcherQueue {

    private final BlockingQueue<AbstractMessage> queue;
    private final int capacity;
    private RoutingKey routingKey;

    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pauseCondition = pauseLock.newCondition();
    private volatile boolean isPaused = false;

    protected MessageDispatcherQueue(final RoutingKey routingKey, final int capacity) {
        this.capacity = capacity;
        this.queue = new PriorityBlockingQueue<>(capacity);
        this.routingKey = routingKey;
    }

    @Override
    public RoutingKey route() {
        return routingKey;
    }

    @Override
    public boolean equalsTo(final RoutingKey key) {
        return routingKey.equals(key);
    }

    @Override
    public AbstractMessage enqueue(final AbstractMessage message) {
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
    public AbstractMessage peek() {
        return queue.peek();
    }

    @Override
    public boolean hasNext() {
        return queue.iterator().hasNext();
    }

    @Override
    public AbstractMessage next() {
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
    public List<AbstractMessage> pressure() {
        List<AbstractMessage> messages = new ArrayList<>(queue.size());
        messages.addAll(queue);
        return messages;
    }

    @Override
    public AbstractMessage dispatch() {
        return queue.poll();
    }

    @Override
    public void updateRoutingKey(final RoutingKey key) {
        if(key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        synchronized (this) {
            this.routingKey = key;
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
            throw new RuntimeException(e);
        } finally {
            pauseLock.unlock();
        }
    }
}
