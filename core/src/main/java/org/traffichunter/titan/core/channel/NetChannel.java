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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.NullMarked;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;

/**
 * @author yun
 */
@NullMarked
public interface NetChannel extends Channel {

    static NetChannel open(EventLoop eventLoop) throws IOException {
        return new NewIONetChannel(eventLoop);
    }

    @Override
    <T> NetChannel setOption(SocketOption<T> option, T value);

    default Promise<Void> connect(String host, int port) {
        return connect(new InetSocketAddress(host, port));
    }

    @CanIgnoreReturnValue
    Promise<Void> connect(InetSocketAddress remote);

    @CanIgnoreReturnValue
    Promise<Void> disconnect();

    @CanIgnoreReturnValue
    Promise<Void> read(Buffer buffer);

    @CanIgnoreReturnValue
    Promise<Void> write(Buffer buffer);

    @CanIgnoreReturnValue
    Promise<Void> writeAndFlush(Buffer buffer);

    @CanIgnoreReturnValue
    Promise<Void> flush();

    boolean isFinishConnected();

    boolean isConnected();
}
