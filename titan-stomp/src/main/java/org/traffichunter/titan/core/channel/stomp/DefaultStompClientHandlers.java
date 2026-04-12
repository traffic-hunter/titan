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
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

public final class DefaultStompClientHandlers {

    public static final class DefaultConnectedHandler implements StompClientCommandHandler {
        @Override
        public void handle(StompClientEvent event, StompClientHandlerContext context) {
            StompFrame frame = event.frame();
            StompClientConnection connection = event.connection();
            connection.connected();

            String heartbeat = frame.getHeader(Elements.HEART_BEAT);
            if (heartbeat != null) {
                StompFrame.HeartBeat server = StompFrame.HeartBeat.doParse(heartbeat);
                long ping = StompFrame.HeartBeat.computePingClientToServer(StompFrame.HeartBeat.DEFAULT, server);
                long pong = StompFrame.HeartBeat.computePongServerToClient(StompFrame.HeartBeat.DEFAULT, server);
                connection.setHeartbeat(ping, pong, () -> connection.send(StompFrame.PING));
            }
        }
    }

    public static final class DefaultMessageHandler implements StompClientCommandHandler {
        @Override
        public void handle(StompClientEvent event, StompClientHandlerContext context) {
            StompFrame frame = event.frame();
            StompClientConnection connection = event.connection();
            String id = frame.getHeader(Elements.SUBSCRIPTION);
            connection.subscriptions()
                    .stream()
                    .filter(subscription -> subscription.id().equals(id))
                    .forEach(subscription -> subscription.getHandler().handle(frame));
        }
    }

    public static final class DefaultReceiptHandler implements StompClientCommandHandler {
        @Override
        public void handle(StompClientEvent event, StompClientHandlerContext context) {
            String receiptId = event.frame().getHeader(Elements.RECEIPT_ID);
            if (receiptId != null) {
                event.connection().receipt(receiptId);
            }
        }
    }

    public static final class DefaultErrorHandler implements StompClientCommandHandler {
        @Override
        public void handle(StompClientEvent event, StompClientHandlerContext context) {
            StompClientConnection connection = event.connection();
            connection.failConnect(new StompException("Received ERROR frame from server"));
            connection.error(event.frame());
        }
    }

    public static final class DefaultPingHandler implements StompClientCommandHandler {
        @Override
        public void handle(StompClientEvent event, StompClientHandlerContext context) {
        }
    }

    private DefaultStompClientHandlers() {}
}
