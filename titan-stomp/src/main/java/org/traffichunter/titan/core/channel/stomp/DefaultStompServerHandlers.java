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

import static org.traffichunter.titan.core.codec.stomp.StompFrame.create;
import static org.traffichunter.titan.core.codec.stomp.StompFrame.errorFrame;
import static org.traffichunter.titan.core.codec.stomp.StompFrame.formatString;
import static org.traffichunter.titan.core.codec.stomp.StompVersion.STOMP_1_0;

import java.util.Optional;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.codec.stomp.Transaction;
import org.traffichunter.titan.core.codec.stomp.Transactions;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.secure.auth.authentication.UsernamePasswordCredentials;

final class DefaultStompServerHandlers {

    public static final class DefaultConnectHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();

            String accept = Optional.ofNullable(sf.getHeader(Elements.ACCEPT_VERSION))
                    .orElse(STOMP_1_0.getVersion());

            if(!accept.equals(sc.version())) {
                sc.send(errorFrame("Not match stomp version...", formatString("Not match stomp version... client version = {}", accept)));
                sc.close();
                return;
            }

            if(context.option().secured()) {
                String login = sf.getHeader(Elements.LOGIN);
                String passcode = sf.getHeader(Elements.PASSCODE);

                if(login == null || passcode == null) {
                    sc.send(errorFrame("Authentication required", "login/passcode is required"));
                    sc.close();
                    return;
                }

                boolean authenticated = context.authentication().authenticate(new UsernamePasswordCredentials(login, passcode));
                if(!authenticated) {
                    sc.send(errorFrame("Authentication failed", "The connection frame does not contain valid credentials."));
                    sc.close();
                    return;
                }
            }

            String heartbeat = Optional.ofNullable(sf.getHeader(Elements.HEART_BEAT))
                    .orElse(StompFrame.HeartBeat.ZERO.value());
            long ping = StompFrame.HeartBeat.computePingClientToServer(
                    StompFrame.HeartBeat.DEFAULT,
                    StompFrame.HeartBeat.doParse(heartbeat)
            );
            long pong = StompFrame.HeartBeat.computePongServerToClient(
                    StompFrame.HeartBeat.DEFAULT,
                    StompFrame.HeartBeat.doParse(heartbeat)
            );

            sc.setHeartbeat(ping, pong, () -> sc.send(StompFrame.PING));

            StompFrame frame = create(StompHeaders.create(), StompCommand.CONNECTED);
            frame.addHeader(Elements.SESSION, sc.session());
            frame.addHeader(Elements.VERSION, sc.version());
            frame.addHeader(Elements.SERVER, IdGenerator.name());
            frame.addHeader(Elements.HEART_BEAT, StompFrame.HeartBeat.DEFAULT.value());
            sc.send(frame);

            context.receipt(sf, sc);
        }
    }

    public static final class DefaultDisconnectHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            context.receipt(event.frame(), event.connection());
            event.connection().close();
        }
    }

    public static final class DefaultSubscribeHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();

            String destination = sf.getHeader(Elements.DESTINATION);
            String id = sf.getHeader(Elements.ID);
            String ack = sf.getHeader(Elements.ACK);
            if(ack == null) {
                ack = StompFrame.AckMode.AUTO;
            }

            if(id == null || destination == null) {
                sc.send(errorFrame("Failed to subscribe.", formatString("Failed to subscribe destination = {}",
                        destination == null ? "null" : destination)));
                sc.close();
                return;
            }

            final Destination dest = Destination.create(destination);
            boolean registered = context.serverConnection().subscriptions().register(
                    StompServerSubscription.builder()
                            .id(id)
                            .ackMode(ack)
                            .destination(dest)
                            .connection(sc)
                            .build()
            );
            if (!registered) {
                sc.send(errorFrame("Failed to subscribe.", formatString("Failed to subscribe destination = {}", destination)));
                sc.close();
                return;
            }

            context.receipt(sf, sc);
        }
    }

    public static final class DefaultUnsubscribeHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();

            String id = sf.getHeader(Elements.ID);
            if(id == null) {
                sc.send(errorFrame("Wrong unsubscribe id.", "Wrong unsubscribe id, Id is required."));
                sc.close();
                return;
            }

            StompServerSubscription subscription = context.serverConnection().subscriptions().unregister(id);
            if (subscription == null) {
                sc.send(errorFrame("Failed to unsubscribe.", formatString("Failed to unsubscribe destination = {}", id)));
                sc.close();
                return;
            }

            context.receipt(sf, sc);
        }
    }

    public static final class DefaultSendHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();
            String destination = sf.getHeader(Elements.DESTINATION);
            if(destination == null) {
                sc.send(errorFrame("Wrong send.", "Wrong send destination id, Id is required."));
                sc.close();
                return;
            }

            Destination dest = Destination.create(destination);
            var subscriptions = context.serverConnection().subscriptions().findByDestination(dest);
            if (subscriptions.isEmpty()) {
                if (context.option().sendErrorOnNoSubscriptions()) {
                    sc.send(errorFrame("No subscriptions found.",
                            formatString("No subscriptions found for destination = {}", destination)));
                    sc.close();
                }
                return;
            }

            int success = 0;
            for (StompServerSubscription subscription : subscriptions) {
                try {
                    StompFrame messageFrame = create(StompHeaders.create(), StompCommand.MESSAGE, sf.getBody());
                    messageFrame.addHeader(Elements.DESTINATION, destination);
                    messageFrame.addHeader(Elements.SUBSCRIPTION, subscription.id());
                    messageFrame.addHeader(Elements.MESSAGE_ID, IdGenerator.uuid());

                    String contentType = sf.getHeader(Elements.CONTENT_TYPE);
                    if (contentType != null) {
                        messageFrame.addHeader(Elements.CONTENT_TYPE, contentType);
                    }

                    subscription.getConnection().send(messageFrame);
                    success++;
                } catch (Exception ignore) {
                }
            }

            if (success == 0) {
                sc.send(errorFrame("Failed to send.", formatString("Failed to send destination = {}", destination)));
                sc.close();
                return;
            }

            context.receipt(sf, sc);
        }
    }

    public static final class DefaultAckHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();

            String id = sf.getHeader(Elements.ID);
            if(id == null) {
                sc.send(errorFrame("Wrong ack id, Id is required.", "Wrong ack id, Id is required."));
                sc.close();
                return;
            }

            String txId = sf.getHeader(Elements.TRANSACTION);
            if(txId != null) {
                Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
                if(transaction == null) {
                    sc.send(errorFrame("Unknown transaction", formatString("Unknown transaction id = {}", txId)));
                    sc.close();
                }
                return;
            }

            if(!Transactions.getInstance().registerTransaction(sc, id)) {
                Transactions.getInstance().removeTransactions(sc);
                sc.send(errorFrame("Failed transaction", formatString("Failed transaction add id = {}", id)));
                sc.close();
                return;
            }

            context.receipt(sf, sc);
        }
    }

    public static final class DefaultNackHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();

            String id = sf.getHeader(Elements.ID);
            if(id == null) {
                sc.send(errorFrame("Wrong nack id, Id is required.", "Wrong nack id, Id is required."));
                sc.close();
                return;
            }

            String txId = sf.getHeader(Elements.TRANSACTION);
            if(txId != null) {
                Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
                if(transaction == null) {
                    sc.send(errorFrame("Unknown transaction.", formatString("Unknown transaction id = {}", txId)));
                    sc.close();
                }
                return;
            }

            if(!Transactions.getInstance().registerTransaction(sc, id)) {
                Transactions.getInstance().removeTransactions(sc);
                sc.send(errorFrame("Failed to transaction.", formatString("Failed to transaction add id = {}", id)));
                sc.close();
                return;
            }

            context.receipt(sf, sc);
        }
    }

    public static final class DefaultBeginHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();

            String txId = sf.getHeader(Elements.TRANSACTION);
            if (txId == null) {
                sc.send(errorFrame("Transaction id is required.", "Transaction id is required."));
                sc.close();
                return;
            }

            if(!Transactions.getInstance().registerTransaction(sc, txId)) {
                sc.send(errorFrame("Transaction id already exists.", formatString("Transaction id = {}", txId)));
                sc.close();
                return;
            }

            context.receipt(sf, sc);
        }
    }

    public static final class DefaultAbortHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();

            String txId = sf.getHeader(Elements.TRANSACTION);
            if (txId == null) {
                sc.send(errorFrame("Transaction id is required.", "Transaction id is required."));
                sc.close();
                return;
            }

            if (!Transactions.getInstance().removeTransaction(sc, txId)) {
                sc.send(errorFrame("Unknown transaction.", formatString("Unknown transaction id = {}", txId)));
                sc.close();
                return;
            }

            context.receipt(sf, sc);
        }
    }

    public static final class DefaultCommitHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
            StompFrame sf = event.frame();
            StompClientConnection sc = event.connection();
            String txId = sf.getHeader(Elements.TRANSACTION);
            if(txId == null) {
                sc.send(errorFrame("Transaction id is required.", "Transaction id is required."));
                sc.close();
                return;
            }

            Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
            if(transaction == null) {
                sc.send(errorFrame("Unknown transaction", formatString("Unknown transaction id = {}", txId)));
                sc.close();
                return;
            }

            StompServerCommandHandler sendHandler = new DefaultSendHandler();
            for (StompFrame frame : transaction.getFrames()) {
                if (frame.getCommand() == StompCommand.SEND) {
                    sendHandler.handle(new StompServerEvent(frame, sc), context);
                }
            }

            transaction.clear();
            Transactions.getInstance().removeTransaction(sc, txId);
            context.receipt(sf, sc);
        }
    }

    public static final class DefaultPingHandler implements StompServerCommandHandler {
        @Override
        public void handle(StompServerEvent event, StompServerHandlerContext context) {
        }
    }

    private DefaultStompServerHandlers() { }
}
