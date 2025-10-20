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
import java.util.Iterator;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.channel.ChannelContext;

/**
 * @author yungwang-o
 */
@Slf4j
public class SecondaryNioEventLoop extends AbstractNioEventLoop {

    private final Handler<ChannelContext> readHandler;

    public SecondaryNioEventLoop(final String eventLoopName,
                                 final int isPendingMaxTasksCapacity,
                                 final Handler<ChannelContext> readHandler) {

        super(eventLoopName, isPendingMaxTasksCapacity);
        this.readHandler = readHandler;
    }

    public void register(final ChannelContext ctx) {
        registerPendingTask(() -> doRegister(ctx));
        selector.wakeup();
    }

    private void doRegister(final ChannelContext ctx) {
        try {
            ctx.socketChannel().register(selector, SelectionKey.OP_READ, ctx);
        } catch (IOException e) {
            log.error("Error registering channel", e);
            try {
                ctx.close();
            } catch (IOException ignored) { }
        }
    }

    @Override
    protected void handleSelectorKeys(final Set<SelectionKey> keySet) {
        Iterator<SelectionKey> iter = keySet.iterator();
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();

            if (!key.isValid()) {
                continue;
            }

            if(key.isReadable()) {
                ChannelContext ctx = ChannelContext.select(key);
                readHandler.handle(ctx);
            }
        }
    }
}