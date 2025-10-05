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
package org.traffichunter.titan.core.transport.stomp;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.*;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHandler;
import org.traffichunter.titan.core.codec.stomp.StompHandlerImpl;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompVersion;
import org.traffichunter.titan.core.dispatcher.Dispatcher;
import org.traffichunter.titan.core.transport.InetServer;

/**
 * @author yungwang-o
 */
@Slf4j
final class DefaultStompServer implements StompServer {

    private final InetServer inetServer;

    // Considering event loop..
    private final ScheduledExecutorService schedule;

    private final StompVersion version = StompVersion.STOMP_1_2;
    private final StompHandler handler = new StompHandlerImpl(Dispatcher.getDefault());

    // timer handling
    private final Map<Long, Future<?>> tasks = new ConcurrentHashMap<>();
    private final AtomicLong timer = new AtomicLong();

    public DefaultStompServer(final InetSocketAddress address) {
        this(InetServer.open(address));
    }

    public DefaultStompServer(final InetServer inetServer) {
        this.inetServer = inetServer;
        this.schedule = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void start() {
        inetServer.start();
    }

    @Override
    public Future<StompServer> listen() {
        return inetServer.listen().thenApply(server -> {
            server.onConnect(channel -> {
                StompServerConnection ssc = new StompServerConnectionImpl(this, channel);
                StompFrame frame = create(StompHeaders.create(), StompCommand.CONNECT);
                handler.handle(frame, ssc);
            });

            return this;
        });
    }

    @Override
    public StompServer onRead(final StompHandler handler) {
        inetServer.onRead(data -> {});
        return this;
    }

    @Override
    public StompServer onWrite(final StompHandler handler) {
        // TODO write handler

        inetServer.onWrite(null);
        return this;
    }

    @Override
    public String host() {
        return inetServer.host();
    }

    @Override
    public int activePort() {
        return inetServer.activePort();
    }

    @Override
    public boolean isStart() {
        return inetServer.isStart();
    }

    @Override
    public boolean isListening() {
        return inetServer.isListening();
    }

    @Override
    public boolean isClosed() {
        return inetServer.isClosed();
    }

    @Override
    public String getVersion() {
        return version.getVersion();
    }

    @Override
    public long setInterval(final long delay, final boolean fixedRate, final TimeUnit unit, final Runnable handler) {
        if(delay <= 0) {
            throw new IllegalArgumentException("delay must be greater than zero");
        }

        final long timerId = timer.getAndIncrement();

        ScheduledFuture<?> task;
        if(fixedRate) {
            task = schedule.scheduleAtFixedRate(handler, delay, delay, TimeUnit.MILLISECONDS);
        } else {
            task = schedule.schedule(handler, delay, TimeUnit.MILLISECONDS);
        }

        tasks.put(timerId, task);

        return timerId;
    }

    @Override
    public void close() {
        inetServer.close();
    }

    @Override
    public void cancelInterval(final long timerId) {
        Future<?> task = tasks.remove(timerId);

        if(task != null) {
            task.cancel(false);
        }
    }
}
