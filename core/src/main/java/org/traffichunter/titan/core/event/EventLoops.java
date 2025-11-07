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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.channel.ChannelContext;
import org.traffichunter.titan.core.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.channel.RoundRobinChannelPropagator;
import org.traffichunter.titan.core.util.event.IOType;

/**
 * @author yungwang-o
 */
@Slf4j
public final class EventLoops {

    private final PrimaryNioEventLoop primaryEventLoop;

    private final List<SecondaryNioEventLoop> secondaryEventLoops;

    private ChannelContextInBoundHandler inBoundHandler;

    private final RoundRobinChannelPropagator<SecondaryNioEventLoop> propagator;

    private final EventLoopBridge<ChannelContext> bridge = EventLoopBridges.getInstance(100);

    private final int nThread;

    public EventLoops() {
        this(Runtime.getRuntime().availableProcessors() * 2);
    }

    public EventLoops(final int nThread) {
        this.primaryEventLoop = EventLoopFactory.createPrimaryEventLoop(bridge);
        this.nThread = nThread;
        this.secondaryEventLoops = new ArrayList<>();
        this.propagator = new RoundRobinChannelPropagator<>(secondaryEventLoops);
    }

    public void start() {
        secondaryEventLoops.addAll(initializeSecondaryEventLoops());
        primaryEventLoop.start();
        propagateTask();
        secondaryEventLoops.forEach(EventLoop::start);
    }

    public void registerHandler(final ChannelContextInBoundHandler inBoundHandler) {
        Objects.requireNonNull(inBoundHandler, "inBoundHandler");

        this.inBoundHandler = inBoundHandler;
    }

    private void propagateTask() {

        Thread.ofVirtual().name("EventBridgeThread", 1).start(() -> {
            log.info("EventLoopBridge start!!");

            while (primaryEventLoop.getLifeCycle().isStarting()) {

                try {
                    ChannelContext ctx = bridge.consume();
                    if (ctx == null) {
                        continue;
                    }

                    SecondaryNioEventLoop sel = propagator.next();
                    sel.register(ctx, IOType.READ);
                } catch (Exception e) {
                    log.error("Failed to register secondary event loop = {}", e.getMessage());
                }
            }
        });
    }

    public void shutdown() {
        shutdown(10, TimeUnit.SECONDS);
    }

    public void shutdown(final long timeout, final TimeUnit unit) {
        log.info("Shutting down EventLoop");

        primaryEventLoop.gracefullyShutdown(timeout, unit);
        secondaryEventLoops.forEach(
                secondaryNioEventLoop -> secondaryNioEventLoop.gracefullyShutdown(timeout, unit)
        );
    }

    public void close() {
        primaryEventLoop.close();
        secondaryEventLoops.forEach(EventLoop::close);
    }

    public PrimaryNioEventLoop primary() {
        return primaryEventLoop;
    }

    public List<SecondaryNioEventLoop> secondaries() {
        return secondaryEventLoops;
    }

    private List<SecondaryNioEventLoop> initializeSecondaryEventLoops() {
        List<SecondaryNioEventLoop> secondaryEventLoops = new ArrayList<>();

        for(int i = 0; i < nThread; i++) {
            secondaryEventLoops.add(
                    EventLoopFactory.createSecondaryEventLoop(i + 1)
            );
        }

        secondaryEventLoops.forEach(secondaryNioEventLoop ->
                secondaryNioEventLoop.registerChannelContextHandler(inBoundHandler)
        );

        return secondaryEventLoops;
    }
}
