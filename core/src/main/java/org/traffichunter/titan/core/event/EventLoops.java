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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.util.channel.ChannelContextOutBoundHandler;
import org.traffichunter.titan.core.util.channel.RoundRobinChannelPropagator;

/**
 * @author yungwang-o
 */
@Slf4j
public final class EventLoops {

    private final PrimaryNIOEventLoop primaryEventLoop;

    private final List<SecondaryNIOEventLoop> secondaryEventLoops;

    private ChannelContextInBoundHandler inBoundHandler;

    private final RoundRobinChannelPropagator<SecondaryNIOEventLoop> propagator;

    private final int nThread;

    public EventLoops() {
        this(Runtime.getRuntime().availableProcessors() * 2);
    }

    public EventLoops(final int nThread) {
        this.primaryEventLoop = EventLoopFactory.createPrimaryEventLoop();
        this.nThread = nThread;
        this.secondaryEventLoops = new ArrayList<>();
        this.propagator = new RoundRobinChannelPropagator<>(secondaryEventLoops);
    }

    public void start() {
        secondaryEventLoops.addAll(initializeSecondaryEventLoops());
        register();
        primaryEventLoop.start();
        secondaryEventLoops.forEach(EventLoop::start);
    }

    public void registerHandler(final ChannelContextInBoundHandler inBoundHandler) {
        Objects.requireNonNull(inBoundHandler, "inBoundHandler");

        this.inBoundHandler = inBoundHandler;
    }

    public void shutdown() {
        shutdown(10, TimeUnit.SECONDS);
    }

    public void shutdown(final long timeout, final TimeUnit unit) {
        primaryEventLoop.gracefulShutdown(timeout, unit);
        secondaryEventLoops.forEach(
                secondaryNIOEventLoop -> secondaryNIOEventLoop.gracefulShutdown(timeout, unit)
        );
    }

    public void close() {
        primaryEventLoop.close();
        secondaryEventLoops.forEach(EventLoop::close);
    }

    public PrimaryNIOEventLoop primary() {
        return primaryEventLoop;
    }

    public List<SecondaryNIOEventLoop> secondaries() {
        return secondaryEventLoops;
    }

    private void register() {
        primaryEventLoop.registerHandler(ctx -> {
            try {
                SecondaryNIOEventLoop el = propagator.next();
                el.register(ctx);
            } catch (Exception e) {
                log.error("Failed to register secondary event loop = {}", e.getMessage());
                try {
                    ctx.close();
                } catch (IOException ignored) { }
            }
        });
    }

    private List<SecondaryNIOEventLoop> initializeSecondaryEventLoops() {
        List<SecondaryNIOEventLoop> secondaryEventLoops = new ArrayList<>();

        for(int i = 0; i < nThread; i++) {
            secondaryEventLoops.add(
                    EventLoopFactory.createSecondaryEventLoop(inBoundHandler, i + 1)
            );
        }

        return secondaryEventLoops;
    }
}
