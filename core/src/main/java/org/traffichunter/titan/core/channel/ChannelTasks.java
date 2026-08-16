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

import org.traffichunter.titan.core.util.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;

/**
 * Executes public channel operations on their owning event loop.
 *
 * @author yun
 */
final class ChannelTasks {

    private ChannelTasks() {
    }

    static ChannelPromise disconnect(NetChannel channel) {
        return execute(channel, channel::close);
    }

    static ChannelPromise write(NetChannel channel, Buffer buffer) {
        return execute(channel, () -> channel.chain().processChannelWrite(channel, buffer));
    }

    static ChannelPromise writeAndFlush(NetChannel channel, Buffer buffer) {
        return execute(channel, () -> {
            channel.chain().processChannelWrite(channel, buffer);
            channel.internal().flush();
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

    static Promise<NetChannel> accept(NetServerChannel channel) {
        IOEventLoop eventLoop = channel.eventLoop();
        Promise<NetChannel> result = Promise.newPromise(eventLoop);
        Runnable acceptTask = () -> {
            try {
                result.success(channel.internal().accept());
            } catch (Throwable error) {
                result.fail(error);
            }
        };

        if (eventLoop.inEventLoop()) {
            acceptTask.run();
        } else {
            eventLoop.execute(acceptTask);
        }
        return result;
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

    static ChannelPromise execute(NetChannel channel, Runnable task) {
        IOEventLoop eventLoop = channel.eventLoop();
        ChannelPromise result = ChannelPromise.newPromise(eventLoop, channel);
        Runnable operation = () -> {
            try {
                task.run();
                result.success();
            } catch (Throwable error) {
                result.fail(error);
            }
        };

        if (eventLoop.inEventLoop()) {
            operation.run();
        } else {
            try {
                eventLoop.execute(operation);
            } catch (Throwable error) {
                result.fail(error);
            }
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
