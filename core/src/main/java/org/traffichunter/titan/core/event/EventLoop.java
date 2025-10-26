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

import java.util.concurrent.TimeUnit;
import org.traffichunter.titan.bootstrap.LifeCycle;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.util.channel.ChannelContextOutBoundHandler;

/**
 * @author yungwang-o
 */
public interface EventLoop {

    void start();

    void restart();

    EventLoopLifeCycle getLifeCycle();

    void registerChannelContextHandler(ChannelContextInBoundHandler inBoundHandler);

    void registerChannelContextHandler(ChannelContextOutBoundHandler outBoundHandler);

    void exceptionHandler(Handler<Throwable> exceptionHandler);

    void suspend();

    default void gracefullyShutdown(long timeout, TimeUnit unit) {
        shutdown(true, timeout, unit);
    }

    void shutdown(boolean isGraceful, long timeout, TimeUnit unit);

    void close();

    interface EventLoopLifeCycle extends LifeCycle {

        boolean isSuspending();

        boolean isSuspended();

        @Override
        boolean isInitialized();

        @Override
        boolean isStarting();

        boolean isStopping();

        @Override
        boolean isStopped();
    }
}
