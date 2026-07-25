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
package org.traffichunter.titan.core.channel.ssl;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.ChannelHandlerChain;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.concurrent.ChannelPromise;

import javax.net.ssl.SSLEngine;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Instant;

/**
 * @author yun
 */
public interface SslChannel extends Channel {

    SSLEngine sslEngine();

    @Override
    ChannelHandlerChain chain();

    @Override
    default ChannelPromise register(IOEventLoop eventLoop) {
        return Channel.super.register(eventLoop);
    }

    @Override
    ChannelPromise register(IOEventLoop eventLoop, ChannelPromise promise);

    @Override
    IOEventLoop eventLoop();

    @Override
    String id();

    @Override
    String session();

    @Override
    <T> SslChannel setOption(SocketOption<T> option, T value);

    @Override
    @Nullable <T> T getOption(SocketOption<T> option);

    @Override
    Instant lastActivatedAt();

    @Override
    Instant setLastActivatedAt();

    @Override
    @Nullable SocketAddress localAddress();

    @Override
    @Nullable SocketAddress remoteAddress();

    @Override
    boolean isOpen();

    @Override
    boolean isRegistered();

    @Override
    boolean isActive();

    @Override
    boolean isClosed();

    @Override
    void close();
}
