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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.channel.ChannelContext;

/**
 * @author yungwang-o
 */
@Slf4j
final class EventLoopPropagator {

    private static final EventLoopPropagator INSTANCE = new EventLoopPropagator();

    private final BlockingQueue<ChannelContext> bridge = new LinkedBlockingQueue<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    public static EventLoopPropagator getInstance() {
        return INSTANCE;
    }

    public void register(final ChannelContext ctx) {
        bridge.add(ctx);
    }

    public void propagate(final List<EventLoop> eventloopGroup) {
        final int adjustIdx = adjustSignedCount(counter.getAndIncrement(), eventloopGroup.size());

        try(ChannelContext ctx = bridge.take()) {

            eventloopGroup.get(adjustIdx);

        } catch (IOException | InterruptedException e) {
            log.error("", e);
        }
    }

    private static int adjustSignedCount(int count, int mod) {
        return (count & Integer.MAX_VALUE) % mod;
    }

    private EventLoopPropagator() {}
}
