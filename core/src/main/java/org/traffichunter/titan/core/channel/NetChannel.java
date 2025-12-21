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

import org.jspecify.annotations.NonNull;
import org.traffichunter.titan.core.concurrent.EventLoop;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;

/**
 * @author yun
 */
public interface NetChannel extends Channel {

    static NetChannel open(@NonNull EventLoop eventLoop) throws IOException {
        return new NewIONetChannel(eventLoop);
    }

    @Override
    <T> NetChannel setOption(SocketOption<T> option, T value);

    default Promise<Void> connect(@NonNull String host, int port) {
        return connect(new InetSocketAddress(host, port));
    }

    Promise<Void> connect(InetSocketAddress remote);

    Promise<Void> disconnect();

    Promise<Void> read(Buffer buffer);

    Promise<Void> write(Buffer buffer);

    Promise<Void> writeAndFlush(Buffer buffer);

    Promise<Void> flush();

    boolean isFinishConnected();

    boolean isConnected();
}
