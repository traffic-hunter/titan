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
 * Continuation passed to an inbound channel handler.
 *
 * <p>Inbound events flow from the transport toward application protocol handlers. A handler must
 * invoke the matching {@code spark*} method to pass an event to the next handler. It may stop
 * propagation intentionally, for example while buffering an incomplete frame or rejecting an
 * invalid handshake.</p>
 *
 * <p>The supplied chain represents only the handlers following the current handler. Calling it
 * does not restart the pipeline from its head.</p>
 *
 * @author yun
 */
public interface ChannelInBoundHandlerChain {

    /** Propagates the pre-connect event to the next inbound handler. */
    void sparkChannelConnecting(NetChannel channel);

    /** Propagates the completed-connect event to the next inbound handler. */
    void sparkChannelAfterConnected(NetChannel channel);

    /**
     * Propagates received bytes to the next inbound handler.
     *
     * <p>A handler that consumes or retains the buffer without forwarding it becomes responsible
     * for the corresponding buffer lifecycle.</p>
     */
    void sparkChannelRead(NetChannel channel, Buffer buffer);

    /** Propagates an inbound processing failure to the next interested handler. */
    void sparkExceptionCaught(Throwable error);
}
