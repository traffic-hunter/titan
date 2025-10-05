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
import java.nio.channels.Selector;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.Configurations;
import org.traffichunter.titan.core.util.channel.ChannelContextInBoundHandler;

/**
 * @author yungwang-o
 */
@Slf4j
public final class EventLoopFactory {

    private static final int maxTaskPendingCapacity = Configurations.taskPendingCapacity();

    public static PrimaryNIOEventLoop createPrimaryEventLoop() {
        try {
            return new PrimaryNIOEventLoop(Selector.open(), maxTaskPendingCapacity);
        } catch (IOException e) {
            throw new EventLoopException("Failed to create PrimaryNIOEventLoop", e);
        }
    }

    public static SecondaryNIOEventLoop createSecondaryEventLoop(
            final ChannelContextInBoundHandler inBoundHandler,
            final int eventLoopNameCount
    ) {
        Objects.requireNonNull(inBoundHandler, "inBoundHandler is null");

        try {
            return new SecondaryNIOEventLoop(
                    Selector.open(),
                    maxTaskPendingCapacity,
                    inBoundHandler,
                    eventLoopNameCount
            );
        } catch (IOException e) {
            throw new EventLoopException("Failed to create SecondaryNIOEventLoop", e);
        }
    }

    private EventLoopFactory() {}
}
