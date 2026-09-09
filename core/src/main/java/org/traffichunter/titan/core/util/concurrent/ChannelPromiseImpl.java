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
 * Default channel promise implementation.
 *
 * @author yun
 */
final class ChannelPromiseImpl extends PromiseImpl<Void> implements ChannelPromise {

    private static final Runnable NOOP = () -> {};

    private final Channel channel;

    ChannelPromiseImpl(EventLoop eventLoop, Channel channel) {
        super(eventLoop, NOOP);
        this.channel = channel;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ChannelPromise addListener(AsyncListener<Void> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public ChannelPromise await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public ChannelPromise complete(@Nullable Void result, @Nullable Throwable error) {
        super.complete(result, error);
        return this;
    }
}
