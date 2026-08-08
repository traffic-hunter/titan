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
package org.traffichunter.titan.fanout.exporter;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.inet.Frame;
import org.traffichunter.titan.fanout.AggregationResult;

/**
 * Protocol boundary for writing a fanout payload to subscribed clients.
 *
 * <p>The gateway calls exporters after a message has been routed to a
 * destination queue. Implementations should find the currently eligible
 * consumers for the destination and return a {@link AggregationResult} that
 * reports how many writes were attempted and completed.</p>
 */
public interface DispatchExporter {

    String name();

    @CanIgnoreReturnValue
    default AggregationResult export(Destination destination, Frame<?, ?> payload) {
        Buffer buffer = payload.toBuffer();
        try {
            return export(destination, buffer);
        } finally {
            buffer.release();
        }
    }

    @CanIgnoreReturnValue
    default AggregationResult export(Destination destination, Message payload) {
        Buffer buffer = Buffer.heap().alloc(payload.getBody());
        try {
            return export(destination, buffer);
        } finally {
            buffer.release();
        }
    }

    /**
     * Exports a borrowed payload buffer.
     *
     * <p>The buffer is valid only for the duration of this invocation. Implementations that
     * retain the payload asynchronously must copy or retain it and release that reference when
     * delivery completes.</p>
     */
    @CanIgnoreReturnValue
    AggregationResult export(Destination destination, Buffer payload);
}
