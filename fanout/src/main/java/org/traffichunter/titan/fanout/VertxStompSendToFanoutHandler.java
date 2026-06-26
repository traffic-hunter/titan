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
import io.vertx.core.Vertx;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.Frames;
import io.vertx.ext.stomp.ServerFrame;
import io.vertx.ext.stomp.StompServerConnection;
import io.vertx.ext.stomp.utils.Headers;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Vert.x STOMP ingress adapter that publishes SEND frames through Titan fanout.
 */
public final class VertxStompSendToFanoutHandler implements Handler<ServerFrame> {

    private static final Logger log = LoggerFactory.getLogger(VertxStompSendToFanoutHandler.class);

    private final FanoutGateway fanoutGateway;

    public VertxStompSendToFanoutHandler(FanoutGateway fanoutGateway) {
        this.fanoutGateway = fanoutGateway;
    }

    @Override
    public void handle(ServerFrame serverFrame) {
        Frame frame = serverFrame.frame();
        StompServerConnection serverConnection = serverFrame.connection();
        Vertx vertx = serverConnection.server().vertx();

        String destination = frame.getDestination();
        if (destination == null || destination.isBlank()) {
            vertx.runOnContext(v -> {
                log.warn("Rejected Vert.x fanout publish due to missing destination. session={}", serverConnection.session());
                serverConnection.write(Frames.createErrorFrame(
                        "Wrong send.",
                        Headers.create(frame.getHeaders()),
                        "Wrong send destination id, Id is required."
                ));
                serverConnection.close();
            });
            return;
        }

        io.vertx.core.buffer.Buffer body = frame.getBody();
        Message message = Message.builder()
                .destination(Destination.create(destination))
                .createdAt(Instant.now())
                .producerId(serverFrame.connection().session())
                .body(Buffer.alloc(body == null ? new byte[]{} : body.getBytes()))
                .build();

        try {
            CompletableFuture<@Nullable Void> publish = fanoutGateway.publish(message);
            publish.whenComplete((ignored, error) ->
                vertx.runOnContext(v -> {
                    if (error != null) {
                        handlePublishFailure(serverConnection, frame, destination, unwrap(error));
                        return;
                    }

                    Frames.handleReceipt(frame, serverConnection);
                })
            );
        } catch (Exception e) {
            vertx.runOnContext(v -> handlePublishFailure(serverConnection, frame, destination, e));
        }
    }

    private static void handlePublishFailure(StompServerConnection serverConnection, Frame frame, String destination, Throwable error) {
        log.error(
                "Failed to publish Vert.x fanout message. session={}, destination={}",
                serverConnection.session(),
                destination,
                error
        );
        serverConnection.write(Frames.createErrorFrame(
                "Failed to publish.",
                Headers.create(frame.getHeaders()),
                "Failed to publish inbound SEND frame."
        ));
        serverConnection.close();
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return error;
    }
}
