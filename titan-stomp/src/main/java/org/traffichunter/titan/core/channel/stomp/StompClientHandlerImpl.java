/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
    public void handle(StompFrame sf, StompClientConnection sc) {
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
                StompClientConnection connection = event.connection();
                connection.connected();

                String heartbeat = frame.getHeader(StompHeaders.Elements.HEART_BEAT);
                if (heartbeat != null) {
                    StompFrame.HeartBeat server = StompFrame.HeartBeat.doParse(heartbeat);
                    long ping = StompFrame.HeartBeat.computePingClientToServer(StompFrame.HeartBeat.DEFAULT, server);
                    long pong = StompFrame.HeartBeat.computePongServerToClient(StompFrame.HeartBeat.DEFAULT, server);
                    connection.setHeartbeat(ping, pong, () -> connection.send(StompFrame.PING));
                }
            }
        }

        static final class DefaultMessageHandler implements StompClientCommandHandler {
            @Override
            public void handle(StompClientEvent event, StompClientHandlerContext context) {
                StompFrame frame = event.frame();
                StompClientConnection connection = event.connection();
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
                StompClientConnection connection = event.connection();
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
