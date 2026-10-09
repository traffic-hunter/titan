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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerCommandHandler;
import org.traffichunter.titan.core.channel.stomp.StompServerEvent;
import org.traffichunter.titan.core.channel.stomp.StompServerHandlerContext;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.message.DispatchRejectException;
import org.traffichunter.titan.core.message.DispatchResult;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Exceptions;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.errorFrame;

/**
 * Converts inbound STOMP {@code SEND} frames into fanout messages.
 *
 * <p>Validates the required destination header, reads the optional group header,
 * converts the frame body to a Titan {@link Message}, and passes it to
 * {@link DispatchGateway} for routing. Exporters handle protocol-specific writes
 * to subscribers.</p>
 *
 * <p>Confirmed queue refusals use their numeric code in the ERROR message header. Errors
 * retain the request's receipt ID when present, and the connection closes after the ERROR
 * write finishes or fails. Dispatch completion does not guarantee subscriber delivery.</p>
 */
public final class StompSendToFanoutHandler implements StompServerCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(StompSendToFanoutHandler.class);

    private final DispatchGateway dispatchGateway;

    public StompSendToFanoutHandler(DispatchGateway dispatchGateway) {
        this.dispatchGateway = dispatchGateway;
    }

    @Override
    public void handle(StompServerEvent event, StompServerHandlerContext context) {
        StompFrame sf = event.frame();
        StompClientChannel connection = event.connection();
        String destination = sf.getHeader(Elements.DESTINATION);
        if (destination == null || destination.isBlank()) {
            log.warn("Rejected dispatch due to missing destination. session={}", connection.session());
            StompFrame response = errorFrame("Wrong send.", "Wrong send destination id, Id is required.");
            context.sendErrorAndClose(sf, response, connection);
            return;
        }

        String group = sf.getHeader(Elements.GROUP);
        Message message;
        try {
            message = Message.builder()
                    .group(group)
                    .destination(Destination.create(destination))
                    .createdAt(Instant.now())
                    .producerId(connection.session())
                    .body(sf.body())
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Rejected dispatch due to malformed headers. session={}, group={}, destination={}",
                    connection.session(),
                    group,
                    destination
            );
            String reason = e.getMessage();
            StompFrame response = errorFrame("Wrong send.", reason == null ? "Invalid SEND headers." : reason);
            context.sendErrorAndClose(sf, response, connection);
            return;
        }

        CompletableFuture<@Nullable Void> dispatchResult;
        try {
            dispatchResult = dispatchGateway.sparkDispatch(message);
        } catch (Exception error) {
            dispatchResult = CompletableFuture.failedFuture(error);
        }

        dispatchResult.whenComplete((ignored, error) -> {
            if (error == null) {
                context.receipt(sf, connection);
                return;
            }

            Throwable failure = Exceptions.unwrapCompletionException(error);

            DispatchResult result = failure instanceof DispatchRejectException rejected ? rejected.result() : null;
            StompFrame response;
            if (result != null) {
                log.warn("Rejected dispatch. session={}, destination={}, code={}, reason={}", connection.session(), destination, result.code(), result);
                String reason = failure.getMessage();
                response = errorFrame(Integer.toString(result.code()), reason == null ? "Message rejected by destination queue." : reason);
            } else {
                log.error("Failed to dispatch message. session={}, destination={}", connection.session(), destination, failure);
                response = errorFrame("Failed to dispatch.", "Failed to dispatch inbound SEND frame.");
            }

            context.sendErrorAndClose(sf, response, connection);
        });
    }
}
