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
    ChannelPromise addListener(AsyncListener listener);

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
    default ChannelPromise fail(@Nullable Throwable err) {
        return complete(null, err);
    }

    @Override
    default ChannelPromise fail(String message) {
        return complete(null, new PromiseException(message));
    }

    @Override
    ChannelPromise complete(@Nullable Void result, @Nullable Throwable error);
}
