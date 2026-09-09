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
package org.traffichunter.titan.core.util.concurrent;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.EventLoop;

/**
 * Promise specialization for channel operations.
 *
 * <p>Channel promises carry the channel that initiated the operation, making registration,
 * bind, connect, and write results easier to correlate with their owning channel.</p>
 *
 * @author yun
 */
public interface ChannelPromise extends Promise<Void> {

    static ChannelPromise newPromise(Channel channel) {
        return newPromise(channel.eventLoop(), channel);
    }

    static ChannelPromise newPromise(EventLoop eventLoop, Channel channel) {
        return new ChannelPromiseImpl(eventLoop, channel);
    }

    static ChannelPromise failedPromise(Channel channel, Throwable error) {
        return failedPromise(channel.eventLoop(), channel, error);
    }

    static ChannelPromise failedPromise(EventLoop eventLoop, Channel channel, Throwable error) {
        return new ChannelPromiseImpl(eventLoop, channel).fail(error);
    }

    /**
     * Returns the channel associated with this asynchronous operation.
     */
    Channel channel();

    @Override
    ChannelPromise addListener(AsyncListener<Void> listener);

    @Override
    ChannelPromise await() throws InterruptedException;

    @Override
    default ChannelPromise success(@Nullable Void result) {
        return complete(result, null);
    }

    @Override
    default ChannelPromise success() {
        return complete(null, null);
    }

    @Override
    default ChannelPromise fail(Throwable err) {
        return complete(null, err);
    }

    @Override
    default ChannelPromise fail(String message) {
        return complete(null, new PromiseException(message));
    }

    @Override
    ChannelPromise complete(@Nullable Void result, @Nullable Throwable error);
}
