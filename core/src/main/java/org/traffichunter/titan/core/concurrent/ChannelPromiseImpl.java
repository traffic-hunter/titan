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
package org.traffichunter.titan.core.concurrent;

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
    public ChannelPromise addListener(AsyncListener listener) {
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
