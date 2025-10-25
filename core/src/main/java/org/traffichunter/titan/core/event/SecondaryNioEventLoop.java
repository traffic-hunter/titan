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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.Configurations;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.util.channel.ChannelContextOutBoundHandler;
import org.traffichunter.titan.core.util.eventloop.EventLoopConstants;

/**
 * @author yungwang-o
 */
@Slf4j
public class SecondaryNioEventLoop extends AbstractNioEventLoop {

    private ChannelContextInBoundHandler inBoundHandler;
    private ChannelContextOutBoundHandler outBoundHandler;
    private final String eventLoopName;

    public SecondaryNioEventLoop() {
        this(Configurations.taskPendingCapacity());
    }

    public SecondaryNioEventLoop(final int isPendingMaxTasksCapacity) {
        this(EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME, isPendingMaxTasksCapacity);
    }

    public SecondaryNioEventLoop(final String eventLoopName, final int isPendingMaxTasksCapacity) {
        super(eventLoopName, isPendingMaxTasksCapacity);
        this.eventLoopName = eventLoopName;
    }

    @Override
    public void registerChannelContextInboundHandler(final ChannelContextInBoundHandler inBoundHandler) {
        this.inBoundHandler = inBoundHandler;
    }

    @Override
    public void registerChannelContextOutboundHandler(final ChannelContextOutBoundHandler outBoundHandler) {
        this.outBoundHandler = outBoundHandler;
    }

    void register(final ChannelContext ctx) {
        registerPendingTask(() -> doRegister(ctx));
        selector.wakeup();
    }

    private void doRegister(final ChannelContext ctx) {
        if(inEventLoop(eventLoopName)) {
            try {
                ctx.socketChannel().register(selector, SelectionKey.OP_READ, ctx);
            } catch (IOException e) {
                log.error("Error registering channel", e);
                try {
                    ctx.close();
                } catch (IOException ignored) { }
            }
        }
    }

    @Override
    protected void handleIO(final Set<SelectionKey> keySet) {
        Assert.checkNull(inBoundHandler, "Inbound handler is null!!");

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
                    inBoundHandler.handleConnect(ctx);
                    inBoundHandler.handleCompletedConnect(ctx);
                } else if (key.isReadable()) {
                    inBoundHandler.handleRead(ctx);
                    inBoundHandler.handleCompletedConnect(ctx);
                } else if (key.isWritable()) {
                    outBoundHandler.handleWrite(ctx);
                    outBoundHandler.handleCompletedWrite(ctx);
                }
            } catch (CancelledKeyException e) {
                inBoundHandler.handleDisconnect(ctx);
            } catch (Throwable t) {
                inBoundHandler.handleException(ctx, t);
            }
        }
    }
}