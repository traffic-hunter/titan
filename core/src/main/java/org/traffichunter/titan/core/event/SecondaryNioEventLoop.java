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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.channel.ChannelContext;
import org.traffichunter.titan.core.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelContextOutBoundHandler;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.traffichunter.titan.core.util.event.EventLoopConstants;
import org.traffichunter.titan.core.util.event.IOType;

/**
 * @author yungwang-o
 */
@Slf4j
public class SecondaryNioEventLoop extends SingleThreadIOEventLoop {

    private ChannelContextInBoundHandler inBoundHandler;
    private ChannelContextOutBoundHandler outBoundHandler;

    public SecondaryNioEventLoop() {
        this(EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME);
    }

    public SecondaryNioEventLoop(final String eventLoopName) {
        super(eventLoopName);
    }

    @Override
    public void registerChannelContextHandler(final ChannelContextInBoundHandler inBoundHandler) {
        Assert.checkNull(inBoundHandler, "inBoundHandler is null");
        this.inBoundHandler = inBoundHandler;
    }

    @Override
    public void registerChannelContextHandler(final ChannelContextOutBoundHandler outBoundHandler) {
        Assert.checkNull(outBoundHandler, "outBoundHandler is null");
        this.outBoundHandler = outBoundHandler;
    }

    public void registerIoChannel(final ChannelContext ctx, final IOType ioType) {
        Assert.checkNull(ctx, "ctx is null");
        Assert.checkNull(ioType, "ioType is null");

        register(() -> registerIoChannel(ctx, ioType.value()));
    }

    @Override
    public void register(final Runnable runnable) {
        addTask(runnable);
        selector.wakeup();
    }

    private void registerIoChannel(final ChannelContext ctx, final int ops) {
        try {
            SocketChannel channel = ctx.socketChannel();
            SelectionKey selectionKey = channel.keyFor(selector);

            // First register
            if(selectionKey == null) {
                channel.register(selector, ops, ctx);
            } else {
                // Already registered
                int currentOps = selectionKey.interestOps();
                selectionKey.interestOps(currentOps | ops);
            }
        } catch (IOException e) {
            log.error("Error registering channel", e);
            try {
                ctx.close();
            } catch (IOException ignored) { }
        }
    }

    @Override
    protected void handleIO(final Set<SelectionKey> keySet) {
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
                    if (ctx.socketChannel().isConnectionPending()) {
                        try {
                            if (ctx.socketChannel().finishConnect()) {
                                log.debug("completed connect: {}", ctx.socketChannel().getRemoteAddress());
                            }
                        } catch (IOException e) {
                            // Failed socket connection, try again.
                            continue;
                        }
                    }
                    inBoundHandler.handleConnect(ctx);
                    inBoundHandler.handleCompletedConnect(ctx);
                } else if (key.isReadable()) {
                    inBoundHandler.handleRead(ctx);
                    inBoundHandler.handleCompletedRead(ctx);
                } else if (key.isWritable()) {
                    outBoundHandler.handleWrite(ctx);
                    outBoundHandler.handleCompletedWrite(ctx);
                }
            } catch (Exception e) {
                log.error("Failed task", e);
            }
        }
    }
}