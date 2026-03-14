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
package org.traffichunter.titan.core.transport;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yun
 */
@Slf4j
public abstract class AbstractTransport<C extends Channel> {

    private final C channel;
    private final EventLoopGroups eventLoopGroups;

    protected AbstractTransport(C channel, EventLoopGroups eventLoopGroups) {
        this.channel = channel;
        this.eventLoopGroups = eventLoopGroups;
    }

    public abstract void start();

    public boolean isStart() {
        return channel.isOpen();
    }

    public boolean isShutdown() {
        return channel.isClosed() && eventLoopGroups.isShuttingDown();
    }

    public @Nullable SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    public @Nullable SocketAddress localAddress() {
        return channel.localAddress();
    }

    public abstract Promise<Void> send(Buffer buffer);

    public abstract void shutdown(long timeout, TimeUnit unit);

    public String version() {
        return "1.0";
    }

    public C channel() {
        return channel;
    }

    void close(long timeout, TimeUnit unit) {
        channel.close();

        if(channel.isClosed()) {
            eventLoopGroups.gracefullyShutdown(timeout, unit);
            //waitForShutdown(timeout, unit);
        } else {
            throw new IllegalStateException("Failed to close channel");
        }
    }

    private void waitForShutdown(long timeout, TimeUnit unit) {
        long waitNanos = Math.min(unit.toNanos(timeout), TimeUnit.SECONDS.toNanos(1));
        long deadlineNanos = System.nanoTime() + waitNanos;

        while (!eventLoopGroups.isShutdown()) {
            if (System.nanoTime() >= deadlineNanos) {
                log.warn("Timed out waiting for event loop groups to shutdown");
                return;
            }

            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    public boolean isClosed() {
        return channel.isClosed();
    }

    protected EventLoopGroups groups() {
        return eventLoopGroups;
    }
}
