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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;

/**
 * Listening server socket channel.
 *
 * <p>A server channel is registered for accept readiness on the primary event loop. Each
 * successful {@link #accept()} creates a separate {@link NetChannel}; that child channel is
 * then initialized and registered on a secondary event loop for read/write processing.</p>
 *
 * @author yun
 */
public interface NetServerChannel extends Channel {

    static NetServerChannel open(ChannelHandShakeEventListener initializer) throws IOException {
        return new NewIONetServerChannel(initializer);
    }

    @Override
    <T> NetServerChannel setOption(SocketOption<T> option, T value);

    default void bind(String host, int port) throws IOException {
        bind(new InetSocketAddress(host, port));
    }

    /**
     * Binds the listening socket to the given address.
     */
    void bind(InetSocketAddress address) throws IOException;

    /**
     * Accepts one pending child connection, or returns {@code null} when no connection is ready.
     */
    @Nullable NetChannel accept();
}
