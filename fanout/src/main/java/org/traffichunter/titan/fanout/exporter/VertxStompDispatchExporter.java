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

import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompServer;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.AggregationResult;

import java.util.List;

/**
 * @author yun
 */
public final class VertxStompDispatchExporter implements DispatchExporter {

    private final StompServer server;

    public VertxStompDispatchExporter(StompServer server) {
        this.server = Assert.checkNotNull(server, "server");
    }

    @Override
    public String name() {
        return "vertx-stomp";
    }

    @Override
    public AggregationResult export(Destination destination, Buffer payload) {
        Assert.checkState(server.isListening(), "Vert.x STOMP server is not listening");

        io.vertx.ext.stomp.Destination stompDestination = server.stompHandler()
                .getDestination(destination.path());
        if (stompDestination == null) {
            return AggregationResult.completed(List.of(destination), 0, 0, 0);
        }

        int attempted = stompDestination.numberOfSubscriptions();
        int succeeded = 0;
        int failed = 0;

        try {
            Frame frame = new Frame()
                    .setCommand(Command.MESSAGE)
                    .setDestination(destination.path())
                    .setBody(io.vertx.core.buffer.Buffer.buffer(payload.getBytes()));
            frame.addHeader(Frame.DESTINATION, destination.path());
            frame.addHeader(Frame.MESSAGE_ID, IdGenerator.uuid());
            frame.addHeader(Frame.CONTENT_LENGTH, Integer.toString(payload.length()));
            stompDestination.dispatch(null, frame);
            succeeded = attempted;
        } catch (Exception e) {
            failed = attempted;
        }

        return AggregationResult.completed(
                List.of(destination),
                attempted,
                succeeded,
                failed
        );
    }
}
