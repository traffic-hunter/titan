/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.transport;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.concurrent.Promise;

/**
 * @author yungwang-o
 */
public interface InetServer {

    static InetServer open() {
        return new InetServerImpl();
    }

    InetServer start();

    @NonNull
    @CanIgnoreReturnValue
    InetServer group(ChannelEventLoopGroup<ChannelPrimaryIOEventLoop> acceptorGroup,
                     ChannelEventLoopGroup<ChannelSecondaryIOEventLoop> ioGroup);

    @CanIgnoreReturnValue
    <T> InetServer option(SocketOption<T> option, T value);

    @CanIgnoreReturnValue
    <T> InetServer childOption(SocketOption<T> option, T value);

    @CanIgnoreReturnValue
    InetServer channelHandler(Handler<Channel> channelHandler);

    default Promise<Void> listen(String host, int port) {
        return listen(new InetSocketAddress(host, port));
    }

    @CanIgnoreReturnValue
    Promise<Void> listen(InetSocketAddress localAddress);

    @CanIgnoreReturnValue
    InetServer exceptionHandler(Handler<Throwable> handler);

    @Nullable SocketAddress localAddress();

    boolean isStart();

    boolean isListening();

    boolean isClosed();

    void shutdown();
}
