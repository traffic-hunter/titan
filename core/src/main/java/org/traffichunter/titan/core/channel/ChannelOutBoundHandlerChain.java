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

import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Continuation passed to an outbound channel handler.
 *
 * <p>Outbound writes flow from application protocol handlers toward the raw transport. A handler
 * can encode or transform a buffer and then invoke {@link #sparkChannelWrite(NetChannel, Buffer)}
 * to continue from its current position. The continuation never re-enters the pipeline head, so
 * codecs already applied to the write are not executed twice.</p>
 *
 * <p>The terminal chain writes through {@link NetChannel.Internal}, deliberately bypassing the
 * public channel pipeline. Handlers that stop propagation or replace a buffer must honor the
 * channel buffer ownership policy.</p>
 *
 * @author yun
 */
public interface ChannelOutBoundHandlerChain {

    /** Passes an outbound buffer to the next handler or terminal raw write. */
    void sparkChannelWrite(NetChannel channel, Buffer buffer);

    /** Propagates an outbound processing failure to the next interested handler. */
    void sparkExceptionCaught(Throwable error);
}
