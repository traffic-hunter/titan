/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.channel;

import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.buffer.Buffers;

/**
 * I/O event loop responsible for connection connect/read/write readiness.
 *
 * <p>The loop dispatches connect completion, read buffers into the inbound handler chain, and
 * write readiness back to the channel's pending write buffer.</p>
 *
 * @author yungwang-o
 */
public class ChannelSecondaryIOEventLoop extends SingleThreadIOEventLoop {

    private static final Logger log = LoggerFactory.getLogger(ChannelSecondaryIOEventLoop.class);

    public ChannelSecondaryIOEventLoop() {
        this(EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME);
    }

    public ChannelSecondaryIOEventLoop(String eventLoopName) {
        super(eventLoopName);
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

            try {
                if (key.isConnectable()) {
                    chain.processChannelConnecting(channel);
                    if(channel.internal().finishConnect()) {
                        chain.processChannelAfterConnected(channel);

                        this.ioSelector()
                                .unregisterConnect(channel)
                                .registerRead(channel);

                        ((AbstractChannel) channel).accept(channel);

                        if (channel instanceof NewIONetChannel nioNetChannel) {
                            nioNetChannel.completeConnect();
                        }
                    }
                } else if (key.isReadable()) {
                    processRead(channel, chain);
                } else if (key.isWritable()) {
                    channel.internal().flush();
                }
            } catch (Exception e) {
                if (channel.isClosed()) {
                    log.debug("Ignoring I/O event for closed channel. channelId={}", channel.id());
                } else {
                    log.error("Failed to process I/O event. channelId={}", channel.id(), e);
                }
                key.cancel();
                try {
                    channel.close();
                } catch (Exception closeError) {
                    log.error("Failed to close channel after I/O error. channelId={}", channel.id(), closeError);
                }
            }
        }
    }

    private void processRead(NetChannel channel, ChannelHandlerChain chain) {
        Buffer buffer = Buffer.direct().alloc(Buffers.DEFAULT_INITIAL_CAPACITY, Buffers.DEFAULT_MAX_CAPACITY);
        final int read;
        try {
            read = channel.internal().read(buffer);
        } catch (Exception e) {
            buffer.release();
            throw e;
        }

        if (read > 0) {
            chain.processChannelRead(channel, buffer);
        } else {
            buffer.release();
        }
    }
}
