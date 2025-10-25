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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.channel.ChannelContext;
import org.traffichunter.titan.core.util.channel.ChannelContextInBoundHandler;
import org.traffichunter.titan.core.util.channel.ChannelContextOutBoundHandler;

@Slf4j
public class PrimaryNioEventLoop extends AbstractNioEventLoop {

    private final EventLoopBridge<ChannelContext> bridge;

    public PrimaryNioEventLoop(final String eventLoopName,
                               final int isPendingMaxTasksCapacity,
                               final EventLoopBridge<ChannelContext> bridge) {

        super(eventLoopName, isPendingMaxTasksCapacity);
        this.bridge = bridge;
    }

    public void registerIoConcern(final SelectableChannel channel) {
        try {
            channel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            throw new EventLoopException("Failed to register server channel", e);
        }
    }

    @Override
    public void registerChannelContextInboundHandler(final ChannelContextInBoundHandler inBoundHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerChannelContextOutboundHandler(final ChannelContextOutBoundHandler outBoundHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void handleIO(final Set<SelectionKey> keySet) {
        Iterator<SelectionKey> iter = keySet.iterator();
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();

            if(!key.isValid()) {
                continue;
            }

            if(key.isAcceptable()) {
                try {
                    ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                    SocketChannel clientSocketChannel = serverChannel.accept();
                    clientSocketChannel.configureBlocking(false);

                    ChannelContext ctx = ChannelContext.create(clientSocketChannel);

                    bridge.produce(ctx);

                    log.info("Accepted connection from {}", clientSocketChannel.getRemoteAddress());
                } catch (IOException e) {
                    log.error("Failed to accept connection = {}", e.getMessage());
                    key.cancel();
                }
            }
        }
    }
}