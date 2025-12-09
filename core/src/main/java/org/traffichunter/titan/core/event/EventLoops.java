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
package org.traffichunter.titan.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.Configurations;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.event.EventLoopConstants;
import org.traffichunter.titan.core.util.event.IOType;

/**
 * @author yungwang-o
 */
@Slf4j
public final class EventLoops {

    private final ChannelPrimaryIOEventLoop primaryEventLoop;
    private final List<ChannelSecondaryIOEventLoop> secondaryEventLoops;
    private final RoundRobinSelector<ChannelSecondaryIOEventLoop> propagator;

    private final EventLoopBridge<ChannelContext> bridge;

    private final int nThread;

    private Handler<ChannelContext> channelContextHandler;

    public EventLoops() {
        this(Runtime.getRuntime().availableProcessors() * 2);
    }

    public EventLoops(final int nThread) {
        this.bridge = EventLoopBridges.getInstance();
        this.primaryEventLoop = EventLoopFactory.createPrimaryIOEventLoop();
        this.nThread = nThread;
        this.secondaryEventLoops = new ArrayList<>();
        this.propagator = new RoundRobinSelector<>(secondaryEventLoops);
    }

    public void start() {
        secondaryEventLoops.addAll(initializeSecondaryEventLoops());
        primaryEventLoop.start();
        secondaryEventLoops.forEach(ChannelSecondaryIOEventLoop::start);
        secondaryEventLoops.forEach(sel -> {
            if(!sel.isStarted()) {
                throw new IllegalStateException("The event loop is not started");
            }
        });

        propagateTask();
    }

    private void propagateTask() {

        new Thread(() -> {
            while (primaryEventLoop.isStarted()) {

                try {
                    ChannelContext ctx = bridge.consume();
                    if (ctx == null) {
                        continue;
                    }

                    channelContextHandler.handle(ctx);
                    ChannelSecondaryIOEventLoop sel = propagator.next();
                    sel.registerIoChannel(ctx, IOType.READ);
                } catch (Exception e) {
                    log.error("Failed to register secondary event loop = {}", e.getMessage());
                }
            }
        }, "EventBridgeThread").start();
    }

    public void registerChannel(final Handler<ChannelContext> handler) {
        Assert.checkNull(handler, "Channel handler cannot be null");
        this.channelContextHandler = handler;
    }

    public void gracefullyShutdown() {
        gracefullyShutdown(EventLoopConstants.DEFAULT_SHUTDOWN_TIME_OUT, TimeUnit.SECONDS);
    }

    public void gracefullyShutdown(final long timeout, final TimeUnit unit) {
        log.info("Shutting down EventLoop");

        primaryEventLoop.gracefullyShutdown(timeout, unit);
        secondaryEventLoops.forEach(el -> el.gracefullyShutdown(timeout, unit));
    }

    public ChannelPrimaryIOEventLoop primary() {
        return primaryEventLoop;
    }

    public List<ChannelSecondaryIOEventLoop> secondaries() {
        return secondaryEventLoops;
    }

    private List<ChannelSecondaryIOEventLoop> initializeSecondaryEventLoops() {
        List<ChannelSecondaryIOEventLoop> secondaryEventLoops = new ArrayList<>();

        for(int i = 0; i < nThread; i++) {
            secondaryEventLoops.add(
                    EventLoopFactory.createSecondaryIOEventLoop(i + 1)
            );
        }

        return secondaryEventLoops;
    }
}
