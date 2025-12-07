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

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.channel.ChannelContext;
import org.traffichunter.titan.core.channel.PrimaryChannelEventLoop;
import org.traffichunter.titan.core.channel.SecondaryChannelEventLoop;
import org.traffichunter.titan.core.util.event.EventLoopConstants;

/**
 * @author yungwang-o
 */
@Slf4j
public final class EventLoopFactory {

    public static PrimaryChannelEventLoop createPrimaryEventLoop(final EventLoopBridge<ChannelContext> bridge) {
        return new PrimaryChannelEventLoop(
                EventLoopConstants.PRIMARY_EVENT_LOOP_THREAD_NAME,
                bridge
        );
    }

    public static SecondaryChannelEventLoop createSecondaryEventLoop(final int eventLoopNameCount) {
        return new SecondaryChannelEventLoop(
                EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME + "-" + eventLoopNameCount
        );
    }

    private EventLoopFactory() {}
}
