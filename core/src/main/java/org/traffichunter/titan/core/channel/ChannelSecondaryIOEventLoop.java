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
package org.traffichunter.titan.core.channel;

import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.event.EventLoopConstants;

/**
 * @author yungwang-o
 */
@Slf4j
public class ChannelSecondaryIOEventLoop extends SingleThreadIOEventLoop {

    public ChannelSecondaryIOEventLoop() {
        this(EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME);
    }

    public ChannelSecondaryIOEventLoop(final String eventLoopName) {
        super(eventLoopName);
    }

    @Override
    public void register(final Runnable runnable) {
        addTask(runnable);
    }

    @Override
    protected void processIO(final Set<SelectionKey> keySet) {
        Iterator<SelectionKey> iter = keySet.iterator();
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();

            if (!key.isValid()) {
                continue;
            }

            ChannelContext ctx = ChannelContext.select(key);

            try {
                if (key.isConnectable()) {
                    if (ctx.isConnected()) {
                        log.debug("completed connect: {}", ctx.remoteAddress());
                    }
                } else if (key.isReadable()) {
                    ctx.chain().fireInboundChannel(ctx);
                } else if (key.isWritable()) {
                    ctx.chain().fireOutboundChannel(ctx);
                }
            } catch (Exception e) {
                log.error("Failed task", e);
            }
        }
    }
}