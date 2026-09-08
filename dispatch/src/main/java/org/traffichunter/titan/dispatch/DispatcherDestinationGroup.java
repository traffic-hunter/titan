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
package org.traffichunter.titan.dispatch;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.IdGenerator;

/**
 * Group backed by its own {@link TrieDispatcher}.
 *
 * <p>The dispatcher carries the group name, so every queue created here registers under
 * a group qualified MBean name. Queue creation takes a read lock and group removal takes
 * the write lock. A group can therefore only be removed while no queue is being created
 * in it, and a stale reference to a removed group fails instead of creating an orphan.</p>
 *
 * @author yun
 */
final class DispatcherDestinationGroup implements DestinationGroup {

    private final String id;
    private final String name;
    private final TrieDispatcher dispatcher;
    private final ReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private volatile boolean removed;

    DispatcherDestinationGroup(String name, long defaultMaxPendingBytes, long defaultResumePendingBytes) {
        this.id = IdGenerator.uuid();
        this.name = name;
        this.dispatcher = new TrieDispatcher(name, defaultMaxPendingBytes, defaultResumePendingBytes);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public @Nullable DispatcherQueue get(Destination destination) {
        return dispatcher.get(destination);
    }

    @Override
    public DispatcherQueue getOrPut(Destination destination) {
        return create(() -> dispatcher.getOrPut(destination));
    }

    @Override
    public DispatcherQueue getOrPut(Destination destination, long maxPendingBytes) {
        return create(() -> dispatcher.getOrPut(destination, maxPendingBytes));
    }

    @Override
    public List<DispatcherQueue> searchAll(Destination destination) {
        return dispatcher.searchAll(destination);
    }

    @Override
    public boolean exists(Destination destination) {
        return dispatcher.exists(destination);
    }

    /** Removes the queue if this group has it. Unknown destinations are ignored. */
    @Override
    public void remove(Destination destination) {
        lifecycle.writeLock().lock();
        try {
            ensureActive();
            if (dispatcher.get(destination) != null) {
                dispatcher.remove(destination);
            }
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    /**
     * Marks the group removed when it holds no queues.
     *
     * @return {@code false} when the group still has queues and stays active
     */
    boolean tryRemove() {
        lifecycle.writeLock().lock();
        try {
            if (!dispatcher.isEmpty()) {
                return false;
            }
            removed = true;
            return true;
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    private DispatcherQueue create(Supplier<DispatcherQueue> action) {
        lifecycle.readLock().lock();
        try {
            ensureActive();
            return action.get();
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    private void ensureActive() {
        if (removed) {
            throw new IllegalStateException("Destination group " + name + " has been removed");
        }
    }

    @Override
    public String toString() {
        return "DestinationGroup[" + name + "]";
    }
}
