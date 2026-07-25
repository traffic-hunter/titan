/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.channel;

import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class NetChannelInternalTest {

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
                    ChannelOutBoundHandlerChainImpl chain
            ) {
                pipelineWrites.incrementAndGet();
                chain.sparkChannelWrite(writtenChannel, buffer);
            }
        });

        eventLoop.start();
        channel.register(eventLoop);

        try {
            Buffer pipelineBuffer = Buffer.alloc("pipeline");
            Promise<Void> publicWrite = channel.writeAndFlush(pipelineBuffer);
            publicWrite.await(2, TimeUnit.SECONDS);
            pipelineBuffer.release();

            assertThat(publicWrite.isSuccess()).isTrue();
            assertThat(pipelineWrites).hasValue(1);
            release(channel.pollWritten());

            Buffer internalBuffer = Buffer.alloc("internal");
            channel.internal().writeAndFlush(internalBuffer);
            internalBuffer.release();

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
