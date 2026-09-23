/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    /** Propagates a write-buffer state transition to the next handler. */
    void sparkChannelWritabilityChanged(NetChannel channel, boolean writable);

    /** Propagates an inbound processing failure to the next interested handler. */
    void sparkExceptionCaught(Throwable error);
}
