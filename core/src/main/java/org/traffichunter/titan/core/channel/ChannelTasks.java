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

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Executes public channel operations on their owning event loop.
 *
 * @author yun
 */
final class ChannelTasks {

    private ChannelTasks() {
    }

    static Promise<Void> connect(
            NetChannel channel,
            InetSocketAddress remote,
            long timeout,
            TimeUnit unit
    ) {
        return execute(channel.eventLoop(), () -> {
            try {
                channel.internal().connect(remote, timeout, unit);
            } catch (IOException e) {
                throw new ChannelException("Failed to connect to " + remote, e);
            }
        });
    }

    static Promise<Void> disconnect(NetChannel channel) {
        return execute(channel.eventLoop(), channel.internal()::disconnect);
    }

    static Promise<Integer> read(NetChannel channel, Buffer buffer) {
        return execute(channel.eventLoop(), () -> channel.internal().read(buffer));
    }

    static Promise<Void> write(NetChannel channel, Buffer buffer) {
        return execute(channel.eventLoop(), () -> channel.chain().processChannelWrite(channel, buffer));
    }

    static Promise<Void> writeAndFlush(NetChannel channel, Buffer buffer) {
        return execute(channel.eventLoop(), () -> {
            channel.chain().processChannelWrite(channel, buffer);
            channel.internal().flush();
        });
    }

    static Promise<Void> flush(NetChannel channel) {
        return execute(channel.eventLoop(), channel.internal()::flush);
    }

    static Promise<Void> onWritabilityChanged(NetChannel channel, boolean isWritable) {
        return execute(
                channel.eventLoop(),
                () -> channel.internal().onWritabilityChanged(isWritable)
        );
    }

    static Promise<Boolean> finishConnect(NetChannel channel) {
        return execute(channel.eventLoop(), () -> {
            try {
                return channel.internal().finishConnect();
            } catch (IOException e) {
                throw new ChannelException("Failed to finish channel connection", e);
            }
        });
    }

    static Promise<Void> bind(NetServerChannel channel, InetSocketAddress address) {
        return execute(channel.eventLoop(), () -> {
            try {
                channel.internal().bind(address);
            } catch (IOException e) {
                throw new ChannelException("Failed to bind to " + address, e);
            }
        });
    }

    static Promise<@Nullable NetChannel> accept(NetServerChannel channel) {
        return execute(channel.eventLoop(), channel.internal()::accept);
    }

    static Promise<Void> execute(IOEventLoop eventLoop, Runnable task) {
        if (!eventLoop.inEventLoop()) {
            return eventLoop.submit(task);
        }

        Promise<Void> result = Promise.newPromise(eventLoop);
        try {
            task.run();
            result.success();
        } catch (Throwable error) {
            result.fail(error);
        }
        return result;
    }

    static <T> Promise<T> execute(IOEventLoop eventLoop, Callable<T> task) {
        if (!eventLoop.inEventLoop()) {
            return eventLoop.submit(task);
        }

        Promise<T> result = Promise.newPromise(eventLoop);
        try {
            result.success(task.call());
        } catch (Throwable error) {
            result.fail(error);
        }
        return result;
    }
}
