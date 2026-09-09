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
package org.traffichunter.titan.dispatch;

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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Vert.x STOMP ingress adapter that publishes SEND frames through Titan fanout.
 */
public final class VertxStompSendToFanoutHandler implements Handler<ServerFrame> {

    private static final Logger log = LoggerFactory.getLogger(VertxStompSendToFanoutHandler.class);

    private final DispatchGateway dispatchGateway;

    public VertxStompSendToFanoutHandler(DispatchGateway dispatchGateway) {
        this.dispatchGateway = dispatchGateway;
    }

    @Override
    public void handle(ServerFrame serverFrame) {
        Frame frame = serverFrame.frame();
        StompServerConnection serverConnection = serverFrame.connection();
        Vertx vertx = serverConnection.server().vertx();

        String destination = frame.getDestination();
        if (destination == null || destination.isBlank()) {
            vertx.runOnContext(v -> {
                log.warn("Rejected Vert.x dispatch due to missing destination. session={}", serverConnection.session());
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
                .body(body == null ? new byte[]{} : body.getBytes())
                .build();

        try {
            CompletableFuture<@Nullable Void> dispatchResult = dispatchGateway.sparkDispatch(message);
            dispatchResult.whenComplete((ignored, error) ->
                vertx.runOnContext(v -> {
                    if (error != null) {
                        handleDispatchFailure(serverConnection, frame, destination, unwrap(error));
                        return;
                    }

                    Frames.handleReceipt(frame, serverConnection);
                })
            );
        } catch (Exception e) {
            vertx.runOnContext(v -> handleDispatchFailure(serverConnection, frame, destination, e));
        }
    }

    private static void handleDispatchFailure(StompServerConnection serverConnection, Frame frame, String destination, Throwable error) {
        log.error(
                "Failed to dispatch Vert.x message. session={}, destination={}",
                serverConnection.session(),
                destination,
                error
        );
        serverConnection.write(Frames.createErrorFrame(
                "Failed to dispatch.",
                Headers.create(frame.getHeaders()),
                "Failed to dispatch inbound SEND frame."
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
