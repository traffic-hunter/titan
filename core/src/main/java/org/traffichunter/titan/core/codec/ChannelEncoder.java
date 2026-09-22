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
package org.traffichunter.titan.core.codec;

import org.traffichunter.titan.core.channel.*;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.ChannelPromise;

/**
 * Outbound channel handler that transforms buffers before they are written.
 *
 * <p>Subclasses implement {@link #encode(NetChannel, Buffer)} and return the encoded
 * buffer to forward. Returning {@code null} drops the outbound event and fails its promise.</p>
 *
 * @author yun
 */
public abstract class ChannelEncoder implements ChannelOutBoundHandler {

    @Override
    public void sparkChannelWrite(
            NetChannel channel,
            Buffer buffer,
            ChannelPromise promise,
            ChannelOutBoundHandlerChain chain
    ) {
        Buffer encoded = encode(channel, buffer);
        if (encoded == null) {
            promise.fail(new ChannelException(getClass().getSimpleName() + " dropped the write"));
            return;
        }
        chain.sparkChannelWrite(channel, encoded, promise);
    }

    @Override
    public void sparkExceptionCaught(Throwable error, ChannelOutBoundHandlerChain chain) {
        chain.sparkExceptionCaught(error);
    }

    /**
     * Encodes an outbound buffer for the given channel.
     *
     * @return the encoded buffer to forward, or {@code null} to stop propagation
     */
    protected abstract @Nullable Buffer encode(NetChannel channel, Buffer buffer);
}
