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

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.channel.stomp.StompServerCommandHandler;
import org.traffichunter.titan.core.channel.stomp.StompServerEvent;
import org.traffichunter.titan.core.channel.stomp.StompServerHandlerContext;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.util.Destination;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.errorFrame;

/**
 * Converts inbound STOMP {@code SEND} frames into fanout messages.
 *
 * <p>This handler is deliberately narrow. It validates the required STOMP
 * destination header, maps the frame body into Titan's internal {@link Message}
 * type, and delegates routing to {@link FanoutGateway}. It does not write
 * directly to subscribers; the exporter layer owns that protocol-specific
 * delivery step.</p>
 */
@Slf4j
public final class StompSendToFanoutHandler implements StompServerCommandHandler {

    private final FanoutGateway fanoutGateway;

    public StompSendToFanoutHandler(FanoutGateway fanoutGateway) {
        this.fanoutGateway = fanoutGateway;
    }

    @Override
    public void handle(StompServerEvent event, StompServerHandlerContext context) {
        StompFrame sf = event.frame();
        StompClientConnection connection = event.connection();
        String destination = sf.getHeader(StompHeaders.Elements.DESTINATION);
        if (destination == null || destination.isBlank()) {
            log.warn("Rejected fanout publish due to missing destination. session={}", connection.session());
            connection.send(errorFrame("Wrong send.", "Wrong send destination id, Id is required."));
            connection.close();
            return;
        }

        Message message = Message.builder()
                .priority(Priority.DEFAULT)
                .destination(Destination.create(destination))
                .createdAt(Instant.now())
                .producerId(connection.session())
                .body(sf.getBody())
                .build();

        try {
            fanoutGateway.publish(message);
        } catch (Exception e) {
            log.error(
                    "Failed to publish fanout message. session={}, destination={}",
                    connection.session(),
                    destination,
                    e
            );
            connection.send(errorFrame("Failed to publish.", "Failed to publish inbound SEND frame."));
            connection.close();
            return;
        }

        context.receipt(sf, connection);
    }
}
