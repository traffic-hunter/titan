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
        try {
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
        } catch (RuntimeException e) {
            result.fail(e);
        }

        return result;
    }

    static Promise<Void> execute(IOEventLoop eventLoop, Runnable task) {
        Promise<Void> result = Promise.newPromise(eventLoop);
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
            } catch (RuntimeException error) {
                result.fail(error);
            }
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
        Promise<T> result = Promise.newPromise(eventLoop);
        Runnable operation = () -> {
            try {
                result.success(task.call());
            } catch (Throwable error) {
                result.fail(error);
            }
        };

        if (eventLoop.inEventLoop()) {
            operation.run();
        } else {
            try {
                eventLoop.execute(operation);
            } catch (RuntimeException error) {
                result.fail(error);
            }
        }
        return result;
    }
}
