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
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.buffer.BufferUtils;
import org.traffichunter.titan.core.util.event.EventLoopConstants;

/**
 * I/O event loop responsible for connection connect/read/write readiness.
 *
 * <p>The loop dispatches connect completion, read buffers into the inbound handler chain, and
 * write readiness back to the channel's pending write buffer.</p>
 *
 * @author yungwang-o
 */
@Slf4j
public class ChannelSecondaryIOEventLoop extends SingleThreadIOEventLoop {

    public ChannelSecondaryIOEventLoop() {
        this(EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME);
    }

    public ChannelSecondaryIOEventLoop(String eventLoopName) {
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

            NetChannel channel = (NetChannel) key.attachment();
            ChannelHandlerChain chain = channel.chain();

            if (key.isConnectable()) {
                chain.processChannelConnecting(channel);
                try {
                    if(channel.finishConnect()) {
                        if (channel instanceof NewIONetChannel nioNetChannel) {
                            nioNetChannel.completeConnect();
                        }

                        chain.processChannelAfterConnected(channel);

                        this.ioSelector()
                                .unregisterConnect(channel)
                                .registerRead(channel);

                        ((AbstractChannel) channel).accept(channel);
                    }
                } catch (Exception e) {
                    log.error("Failed connect", e);
                }
            } else if (key.isReadable()) {
                Buffer buffer = Buffer.alloc(BufferUtils.DEFAULT_INITIAL_CAPACITY, BufferUtils.DEFAULT_MAX_CAPACITY);
                int read = channel.read(buffer);
                if (read > 0) {
                    chain.processChannelRead(channel, buffer);
                } else {
                    buffer.release();
                }
            } else if (key.isWritable()) {
                channel.flush();
            }
        }
    }
}
