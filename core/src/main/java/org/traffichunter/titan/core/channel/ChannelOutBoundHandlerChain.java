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

import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

import java.util.List;

/**
 * Continuation passed to an outbound channel handler.
 *
 * <p>Outbound writes flow from application protocol handlers toward the raw transport. A handler
 * can encode or transform a buffer and then invoke
 * {@link #sparkChannelWrite(NetChannel, Buffer, ChannelPromise)}
 * to continue from its current position. The continuation never re-enters the pipeline head, so
 * codecs already applied to the write are not executed twice.</p>
 *
 * <p>The terminal chain writes through {@link NetChannel.Internal}, deliberately bypassing the
 * public channel pipeline. Handlers that stop propagation or replace a buffer must honor the
 * channel buffer ownership policy.</p>
 *
 * @author yun
 */
public interface ChannelOutBoundHandlerChain {

    /**
     * Passes an outbound buffer to the next handler or terminal raw write.
     *
     * <p>The promise settles when the transport has taken every byte of the buffer.</p>
     */
    void sparkChannelWrite(NetChannel channel, Buffer buffer, ChannelPromise promise);

    /**
     * Passes one request made of several buffers, for a handler that split its input. The
     * transport admits the request whole or not at all and settles the promise once.
     */
    void sparkChannelWrite(NetChannel channel, List<Buffer> buffers, ChannelPromise promise);

    /** Propagates an outbound processing failure to the next interested handler. */
    void sparkExceptionCaught(Throwable error);
}
