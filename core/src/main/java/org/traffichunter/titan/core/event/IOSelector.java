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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.util.concurrent.NewIOException;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

/**
 * @author yun
 */
public final class IOSelector {

    private final Selector selector;

    private IOSelector(Selector selector) {
        this.selector = selector;
    }

    static IOSelector open() {
        try {
            return new IOSelector(Selector.open());
        } catch (IOException e) {
            throw new NewIOException("Selector is not open", e);
        }
    }

    boolean isOpen() {
        return selector.isOpen();
    }

    int invokeEvent() throws IOException {
        return selector.select();
    }

    int invokeEvent(long timeout) throws IOException {
        return selector.select(timeout);
    }

    int invokeNowEvent() throws IOException {
        return selector.selectNow();
    }

    Set<SelectionKey> readyIOEvents() {
        return selector.selectedKeys();
    }

    void wakeUp() {
        selector.wakeup();
    }

    void close() throws IOException {
        selector.close();
    }

    @CanIgnoreReturnValue
    public IOSelector registerAccept(SelectableChannel channel) throws IOException {
        register(channel, SelectionKey.OP_ACCEPT);
        return this;
    }

    @CanIgnoreReturnValue
    public IOSelector registerRead(SelectableChannel channel) throws IOException {
        register(channel, SelectionKey.OP_READ);
        return this;
    }

    @CanIgnoreReturnValue
    public IOSelector registerWrite(SelectableChannel channel) throws IOException {
        register(channel, SelectionKey.OP_WRITE);
        return this;
    }

    @CanIgnoreReturnValue
    public IOSelector registerConnect(SelectableChannel channel) throws IOException {
        register(channel, SelectionKey.OP_CONNECT);
        return this;
    }

    @CanIgnoreReturnValue
    public IOSelector register(SelectableChannel channel, int ops) throws IOException {
        SelectionKey key = channel.keyFor(selector);

        if(key == null) {
            channel.register(selector, ops);
        } else {
            key.interestOps(key.interestOps() | ops);
        }
        wakeUp();
        return this;
    }
}
