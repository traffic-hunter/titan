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
package org.traffichunter.titan.core.event;

import org.traffichunter.titan.core.util.event.IOType;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * @author yun
 */
public final class IOHandler {

    private final Selector selector;

    IOHandler(Selector selector) {
        this.selector = selector;
    }

    public void registerAccept(SelectableChannel channel) throws IOException {
        channel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void registerRead(SelectableChannel channel) throws IOException {
        channel.register(selector, SelectionKey.OP_READ);
    }

    public void registerWrite(SelectableChannel channel) throws IOException {
        channel.register(selector, SelectionKey.OP_WRITE);
    }

    public void registerConnect(SelectableChannel channel) throws IOException {
        channel.register(selector, SelectionKey.OP_CONNECT);
    }

    public void registerIo(SelectableChannel channel, int ops) throws IOException {
        channel.register(selector, ops);
    }
}
