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
package org.traffichunter.titan.core.transport.connection.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.transport.connection.Connection;
import org.traffichunter.titan.core.transport.connection.ConnectionFactory;
import org.traffichunter.titan.core.util.Assert;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yun
 */
public final class SimpleConnectionPool<C extends Connection> implements ConnectionPool<C> {

    private static final Logger log = LoggerFactory.getLogger(SimpleConnectionPool.class);
    private static final long ACQUIRE_WAIT_MILLIS = 100L;

    /**
     * idle connection pool
     */
    private final BlockingQueue<C> idlePool;

    private final ConnectionFactory<C> factory;
    private final Metadata connectionPoolMetadata;
    private final Set<C> leasedConnections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger count = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    public SimpleConnectionPool(Metadata connectionPoolMetadata, ConnectionFactory<C> factory) {
        this.factory = factory;
        this.connectionPoolMetadata = connectionPoolMetadata;
        this.idlePool = new ArrayBlockingQueue<>(connectionPoolMetadata.maxConnections(), connectionPoolMetadata.fair());
        init();
    }

    @Override
    public C acquire() {
        while (true) {
            Assert.checkState(!closed.get(), "Pool is already closed");

            C idleConn = idlePool.poll();
            if (idleConn != null) {
                markAsLeased(idleConn);
                return idleConn;
            }

            int currentCount = count.get();
            if (checkIfNotOverMaxConnections() && count.compareAndSet(currentCount, currentCount + 1)) {
                try {
                    C created = factory.create();
                    markAsLeased(created);
                    return created;
                } catch (Exception e) {
                    decrementCount();
                    throw new IllegalStateException(e);
                }
            }

            try {
                C waitedConn = idlePool.poll(ACQUIRE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                if (waitedConn != null) {
                    markAsLeased(waitedConn);
                    return waitedConn;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for an idle connection", e);
            }
        }
    }

    @Override
    public boolean release(C connection) {
        if (!leasedConnections.remove(connection)) {
            log.warn("Ignoring release request for unknown or already released connection");
            return false;
        }

        if(closed.get()) {
            factory.destroy(connection);
            decrementCount();
            return false;
        }

        boolean offer = idlePool.offer(connection);
        if (!offer) {
            factory.destroy(connection);
            decrementCount();
        }

        return offer;
    }

    @Override
    public int size() {
        return count.get();
    }

    @Override
    public Metadata metadata() {
        return connectionPoolMetadata;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() throws IOException {
        if(!closed.compareAndSet(false, true)) {
            return;
        }

        C conn;
        while ((conn = idlePool.poll()) != null) {
            factory.destroy(conn);
            decrementCount();
        }
    }

    private boolean checkIfNotOverMaxConnections() {
        return count.get() < metadata().maxConnections();
    }

    private void init() {

        for(int i = 0; i < connectionPoolMetadata.minConnections(); i++) {
            C c;
            try {
                c = factory.create();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            idlePool.add(c);
            count.incrementAndGet();
        }
    }

    private void markAsLeased(C conn) {
        Assert.checkState(leasedConnections.add(conn), "Connection is already leased");
    }

    private void decrementCount() {
        do {
            int current = count.get();
            if (current == 0) {
                log.warn("Connection count is already 0 while trying to decrement");
                return;
            }
            if (count.compareAndSet(current, current - 1)) {
                return;
            }
        } while (true);
    }
}
