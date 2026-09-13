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
package org.traffichunter.titan.core.channel.stomp;

import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.util.Handler;

public final class StompClientHandlerImpl implements StompClientHandler {

    private final StompClientHandlerContext context = new StompClientHandlerContext();

    private Handler<StompClientEvent> receivedFrameHandler = event -> {};
    private StompClientCommandHandler connectedHandler = new DefaultStompClientHandlers.DefaultConnectedHandler();
    private StompClientCommandHandler messageHandler = new DefaultStompClientHandlers.DefaultMessageHandler();
    private StompClientCommandHandler receiptHandler = new DefaultStompClientHandlers.DefaultReceiptHandler();
    private StompClientCommandHandler errorHandler = new DefaultStompClientHandlers.DefaultErrorHandler();
    private StompClientCommandHandler pingHandler = new DefaultStompClientHandlers.DefaultPingHandler();

    @Override
    public void handle(StompFrame sf, StompClientChannel sc) {
        sc.setLastActivatedAt();
        StompClientEvent event = new StompClientEvent(sf, sc);
        receivedFrameHandler.handle(event);

        switch (sf.getCommand()) {
            case CONNECTED -> connectedHandler.handle(event, context);
            case MESSAGE -> messageHandler.handle(event, context);
            case RECEIPT -> receiptHandler.handle(event, context);
            case ERROR -> errorHandler.handle(event, context);
            case PING -> pingHandler.handle(event, context);
            default -> throw new IllegalStateException("Unexpected value: " + sf.getCommand());
        }
    }

    @Override
    public StompClientHandler receivedFrameHandler(Handler<StompClientEvent> handler) {
        this.receivedFrameHandler = handler;
        return this;
    }

    @Override
    public StompClientHandler connectedHandler(StompClientCommandHandler handler) {
        this.connectedHandler = handler;
        return this;
    }

    @Override
    public StompClientHandler messageHandler(StompClientCommandHandler handler) {
        this.messageHandler = handler;
        return this;
    }

    @Override
    public StompClientHandler receiptHandler(StompClientCommandHandler handler) {
        this.receiptHandler = handler;
        return this;
    }

    @Override
    public StompClientHandler errorHandler(StompClientCommandHandler handler) {
        this.errorHandler = handler;
        return this;
    }

    @Override
    public StompClientHandler pingHandler(StompClientCommandHandler handler) {
        this.pingHandler = handler;
        return this;
    }

    private static final class DefaultStompClientHandlers {

        static final class DefaultConnectedHandler implements StompClientCommandHandler {
            @Override
            public void handle(StompClientEvent event, StompClientHandlerContext context) {
                StompFrame frame = event.frame();
                StompClientChannel connection = event.connection();
                connection.connected();

                String heartbeat = frame.getHeader(StompHeaders.Elements.HEART_BEAT);
                if (heartbeat != null) {
                    StompFrame.HeartBeat server = StompFrame.HeartBeat.doParse(heartbeat);
                    // Negotiate against what this client offered in CONNECT, not the defaults.
                    StompFrame.HeartBeat client = StompFrame.HeartBeat.create(
                            connection.option().heartbeatX(),
                            connection.option().heartbeatY()
                    );
                    long ping = StompFrame.HeartBeat.computePingClientToServer(client, server);
                    long pong = StompFrame.HeartBeat.computePongServerToClient(client, server);
                    connection.setHeartbeat(ping, pong, () -> connection.send(StompFrame.PING));
                }
            }
        }

        static final class DefaultMessageHandler implements StompClientCommandHandler {
            @Override
            public void handle(StompClientEvent event, StompClientHandlerContext context) {
                StompFrame frame = event.frame();
                StompClientChannel connection = event.connection();
                String id = frame.getHeader(StompHeaders.Elements.SUBSCRIPTION);
                connection.subscriptions()
                        .stream()
                        .filter(subscription -> subscription.id().equals(id))
                        .forEach(subscription -> subscription.getHandler().handle(frame));
            }
        }

        static final class DefaultReceiptHandler implements StompClientCommandHandler {
            @Override
            public void handle(StompClientEvent event, StompClientHandlerContext context) {
                String receiptId = event.frame().getHeader(StompHeaders.Elements.RECEIPT_ID);
                if (receiptId != null) {
                    event.connection().receipt(receiptId);
                }
            }
        }

        static final class DefaultErrorHandler implements StompClientCommandHandler {
            @Override
            public void handle(StompClientEvent event, StompClientHandlerContext context) {
                StompClientChannel connection = event.connection();
                connection.failConnect(new StompException("Received ERROR frame from server"));
                connection.error(event.frame());
            }
        }

        static final class DefaultPingHandler implements StompClientCommandHandler {
            @Override
            public void handle(StompClientEvent event, StompClientHandlerContext context) {
            }
        }

        private DefaultStompClientHandlers() {}
    }
}
