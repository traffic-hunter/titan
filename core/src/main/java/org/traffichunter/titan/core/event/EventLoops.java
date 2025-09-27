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

import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.util.channel.ChannelContextOutBoundHandler;
import org.traffichunter.titan.core.util.concurrent.AdvancedThreadPoolExecutor;

/**
 * @author yungwang-o
 */
public final class EventLoops {

    private final AdvancedThreadPoolExecutor executor;

    private final PrimaryNIOEventLoop primaryEventLoop;

    private final List<SecondaryNIOEventLoop> secondaryEventLoops = new CopyOnWriteArrayList<>();

    private final Handler<Selector> selectorHandler;

    private final ChannelContextInBoundHandler inBoundHandler;

    private final ChannelContextOutBoundHandler outBoundHandler;

    private final int nThread;

    public EventLoops(final PrimaryNIOEventLoop primaryEventLoop,
                      final Handler<Selector> selectorHandler,
                      final ChannelContextInBoundHandler inBoundHandler,
                      final ChannelContextOutBoundHandler outBoundHandler) {

        this(primaryEventLoop, selectorHandler, inBoundHandler, outBoundHandler, Runtime.getRuntime().availableProcessors() * 2);
    }

    public EventLoops(final PrimaryNIOEventLoop primaryEventLoop,
                      final Handler<Selector> selectorHandler,
                      final ChannelContextInBoundHandler inBoundHandler,
                      final ChannelContextOutBoundHandler outBoundHandler,
                      final int nThread) {

        this.primaryEventLoop = primaryEventLoop;
        this.executor = new AdvancedThreadPoolExecutor(
                nThread,
                nThread,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );
        this.selectorHandler = selectorHandler;
        this.inBoundHandler = inBoundHandler;
        this.outBoundHandler = outBoundHandler;
        this.nThread = nThread;
        this.secondaryEventLoops.addAll(initializeSecondaryEventLoops(inBoundHandler, outBoundHandler));
    }



    private List<SecondaryNIOEventLoop> initializeSecondaryEventLoops(
            final ChannelContextInBoundHandler inBoundHandler,
            final ChannelContextOutBoundHandler outBoundHandler
    ) {

        List<SecondaryNIOEventLoop> secondaryEventLoops = new ArrayList<>();

        for(int i = 0; i < nThread; i++) {
            secondaryEventLoops.add(
                    EventLoopFactory.createSecondaryEventLoop(inBoundHandler, outBoundHandler)
            );
        }

        return secondaryEventLoops;
    }
}
