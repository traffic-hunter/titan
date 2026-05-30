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
package org.traffichunter.titan.fanout;

import io.vertx.core.Handler;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.Frames;
import io.vertx.ext.stomp.ServerFrame;
import io.vertx.ext.stomp.utils.Headers;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.time.Instant;

/**
 * Vert.x STOMP ingress adapter that publishes SEND frames through Titan fanout.
 */
@Slf4j
public final class VertxStompSendToFanoutHandler implements Handler<ServerFrame> {

    private final FanoutGateway fanoutGateway;

    public VertxStompSendToFanoutHandler(FanoutGateway fanoutGateway) {
        this.fanoutGateway = fanoutGateway;
    }

    @Override
    public void handle(ServerFrame serverFrame) {
        Frame frame = serverFrame.frame();
        String destination = frame.getDestination();
        if (destination == null || destination.isBlank()) {
            log.warn("Rejected Vert.x fanout publish due to missing destination. session={}", serverFrame.connection().session());
            serverFrame.connection().write(Frames.createErrorFrame(
                    "Wrong send.",
                    Headers.create(frame.getHeaders()),
                    "Wrong send destination id, Id is required."
            ));
            serverFrame.connection().close();
            return;
        }

        io.vertx.core.buffer.Buffer body = frame.getBody();
        Message message = Message.builder()
                .priority(Priority.DEFAULT)
                .destination(Destination.create(destination))
                .createdAt(Instant.now())
                .producerId(serverFrame.connection().session())
                .body(Buffer.alloc(body == null ? new byte[]{} : body.getBytes()))
                .build();

        try {
            fanoutGateway.publish(message);
        } catch (Exception e) {
            log.error(
                    "Failed to publish Vert.x fanout message. session={}, destination={}",
                    serverFrame.connection().session(),
                    destination,
                    e
            );
            serverFrame.connection().write(Frames.createErrorFrame(
                    "Failed to publish.",
                    Headers.create(frame.getHeaders()),
                    "Failed to publish inbound SEND frame."
            ));
            serverFrame.connection().close();
            return;
        }

        Frames.handleReceipt(frame, serverFrame.connection());
    }
}
