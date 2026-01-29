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

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.util.Noop;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yun
 */
@Slf4j
public class ChannelHandlerChain {

    private final ChannelOutBoundHandlerChainImpl outHead;
    private ChannelOutBoundHandlerChainImpl outTail;

    private final ChannelInBoundHandlerChainImpl inHead;
    private ChannelInBoundHandlerChainImpl inTail;

    public ChannelHandlerChain() {
        inHead = inTail = new ChannelInBoundHandlerChainImpl(new ChannelInBoundHandlerHead());
        outHead = outTail = new ChannelOutBoundHandlerChainImpl(new ChannelOutBoundHandlerHead());
    }

    public ChannelHandlerChain add(ChannelInBoundHandler handler) {
        ChannelInBoundHandlerChainImpl context = new ChannelInBoundHandlerChainImpl(handler);
        inTail.next = context;
        inTail = context;

        return this;
    }

    public ChannelHandlerChain add(ChannelOutBoundHandler handler) {
        ChannelOutBoundHandlerChainImpl context = new ChannelOutBoundHandlerChainImpl(handler);
        outTail.next = context;
        outTail = context;

        return this;
    }

    void processChannelConnecting(NetChannel channel) {
        inHead.sparkChannelConnecting(channel);
    }

    void processChannelAfterConnected(NetChannel channel) {
        inHead.sparkChannelAfterConnected(channel);
    }

    void processChannelRead(NetChannel channel, Buffer buffer) {
        try {
            inHead.sparkChannelRead(channel, buffer);
        } catch (Exception e) {
            log.error("Failed to process read", e);
            channel.close();
        }
    }

    void processChannelWrite(NetChannel channel, Buffer buffer) {
        try {
            outHead.sparkChannelWrite(channel, buffer);
        } catch (Exception e) {
            log.error("Failed to process write", e);
            channel.close();
        }
    }

    @Noop
    private static class ChannelInBoundHandlerHead implements ChannelInBoundHandler { }

    @Noop
    private static class ChannelOutBoundHandlerHead implements ChannelOutBoundHandler { }
}
