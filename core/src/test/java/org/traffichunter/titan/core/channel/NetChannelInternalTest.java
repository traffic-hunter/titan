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

import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class NetChannelInternalTest {

    @Test
    void channel_operations_return_promise_bound_to_channel() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("channel-promise-test");
        InMemoryNetChannel channel = new InMemoryNetChannel();
        eventLoop.start();
        channel.register(eventLoop);

        try {
            ChannelPromise connect = channel.connect(
                    new InetSocketAddress("127.0.0.1", 8080),
                    1,
                    TimeUnit.SECONDS
            );
            connect.await(2, TimeUnit.SECONDS);

            assertThat(connect.isSuccess()).isTrue();
            assertThat(connect.channel()).isSameAs(channel);

            ChannelPromise disconnect = channel.disconnect();
            disconnect.await(2, TimeUnit.SECONDS);

            assertThat(disconnect.isSuccess()).isTrue();
            assertThat(disconnect.channel()).isSameAs(channel);
        } finally {
            channel.close();
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void public_write_enters_pipeline_and_internal_write_bypasses_it() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("net-channel-internal-test");
        InMemoryNetChannel channel = new InMemoryNetChannel();
        AtomicInteger pipelineWrites = new AtomicInteger();
        channel.chain().add(new ChannelOutBoundHandler() {
            @Override
            public void sparkChannelWrite(
                    NetChannel writtenChannel,
                    Buffer buffer,
                    ChannelPromise promise,
                    ChannelOutBoundHandlerChain chain
            ) {
                pipelineWrites.incrementAndGet();
                chain.sparkChannelWrite(writtenChannel, buffer, promise);
            }
        });

        eventLoop.start();
        channel.register(eventLoop);

        try {
            Buffer pipelineBuffer = Buffer.heap().alloc("pipeline");
            ChannelPromise publicWrite = channel.writeAndFlush(pipelineBuffer);
            publicWrite.await(2, TimeUnit.SECONDS);
            pipelineBuffer.release();

            assertThat(publicWrite.isSuccess()).isTrue();
            assertThat(publicWrite.channel()).isSameAs(channel);
            assertThat(pipelineWrites).hasValue(1);
            release(channel.pollWritten());

            Buffer internalBuffer = Buffer.heap().alloc("internal");
            ChannelPromise internalWrite = ChannelPromise.newPromise(channel);
            channel.internal().writeAndFlush(internalBuffer, internalWrite);
            internalBuffer.release();

            assertThat(internalWrite.isSuccess()).isTrue();
            assertThat(pipelineWrites).hasValue(1);
            release(channel.pollWritten());
        } finally {
            channel.close();
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    private static void release(@Nullable Buffer buffer) {
        assertThat(buffer).isNotNull();
        if (buffer != null) {
            buffer.release();
        }
    }
}
