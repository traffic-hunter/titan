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
import org.traffichunter.titan.fanout.CompletableResult;

/**
 * Protocol boundary for writing a fanout payload to subscribed clients.
 *
 * <p>The gateway calls exporters after a message has been routed to a
 * destination queue. Implementations should find the currently eligible
 * consumers for the destination and return a {@link CompletableResult} that
 * reports how many writes were attempted and completed.</p>
 */
public interface FanoutExporter {

    String name();

    @CanIgnoreReturnValue
    default CompletableResult export(Destination destination, Frame<?, ?> payload) {
        return export(destination, payload.toBuffer());
    }

    @CanIgnoreReturnValue
    default CompletableResult export(Destination destination, Message payload) {
        return export(destination, Buffer.alloc(payload.getBody()));
    }

    @CanIgnoreReturnValue
    CompletableResult export(Destination destination, Buffer payload);
}
