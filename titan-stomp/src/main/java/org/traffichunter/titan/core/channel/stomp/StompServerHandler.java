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

import static org.traffichunter.titan.core.codec.stomp.StompFrame.*;
import static org.traffichunter.titan.core.codec.stomp.StompVersion.*;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.codec.stomp.*;
import org.traffichunter.titan.core.codec.stomp.StompFrame.AckMode;
import org.traffichunter.titan.core.codec.stomp.StompFrame.HeartBeat;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.dispatcher.Dispatcher;
import org.traffichunter.titan.core.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.RoutingKey;

/**
 * @author yungwang-o
 */
@Slf4j
public final class StompServerHandler implements StompHandler {

    private final Dispatcher dispatcher;

    public StompServerHandler(final Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void handle(final StompFrame sf, final StompNetChannel sc) {
        sc.setLastActivatedAt();

        switch (sf.getCommand()) {
            case STOMP, CONNECT -> doConnect(sf, sc);
            case DISCONNECT -> doDisconnect(sf, sc);
            case SUBSCRIBE -> doSubscribe(sf, sc);
            case UNSUBSCRIBE -> doUnsubscribe(sf, sc);
            case ACK -> doAck(sf, sc);
            case NACK -> doNack(sf, sc);
            case BEGIN -> doBegin(sf, sc);
            case ABORT -> doAbort(sf, sc);
            case COMMIT -> doCommit(sf, sc);
            case SEND -> doSend(sf, sc);
            case PING -> {}
            default -> throw new StompException("Unknown command: " + sf.getCommand());
        }
    }

    private void doBegin(final StompFrame sf, final StompNetChannel sc) {

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

        doReceipt(sf, sc);
    }

    private void doAbort(final StompFrame sf, final StompNetChannel sc) {

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

        doReceipt(sf, sc);
    }

    private void doAck(final StompFrame sf, final StompNetChannel sc) {

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
        } else {
            if(!Transactions.getInstance().registerTransaction(sc, id)) {
                Transactions.getInstance().removeTransactions(sc);
                sc.send(errorFrame("Failed transaction", formatString("Failed transaction add id = {}", id)));
                sc.close();
                return;
            }

            doReceipt(sf, sc);
        }
    }

    private void doCommit(final StompFrame sf, final StompNetChannel sc) {

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

        for (StompFrame frame : transaction.getFrames()) {
            if (frame.getCommand() == StompCommand.SEND) {
                send(frame, sc);
            }
        }

        transaction.clear();
        Transactions.getInstance().removeTransaction(sc, txId);

        doReceipt(sf, sc);
    }

    private void doConnect(final StompFrame sf, final StompNetChannel sc) {

        String accept = Optional.ofNullable(sf.getHeader(Elements.ACCEPT_VERSION))
                .orElse(STOMP_1_0.getVersion());

        // negotiate
        if(!accept.equals(sc.version())) {
            sc.send(errorFrame("Not match stomp version...", formatString("Not match stomp version... client version = {}", accept)));
            sc.close();
            return;
        }

        String heartbeat = Optional.ofNullable(sf.getHeader(Elements.HEART_BEAT))
                .orElse(HeartBeat.ZERO.value());
        long ping = StompFrame.HeartBeat.computePingClientToServer(
                HeartBeat.DEFAULT,
                HeartBeat.doParse(heartbeat)
        );
        long pong = StompFrame.HeartBeat.computePongServerToClient(
                HeartBeat.DEFAULT,
                HeartBeat.doParse(heartbeat)
        );

        sc.setHeartbeat(ping, pong, () -> sc.send(StompFrame.PING));

        StompFrame frame = create(StompHeaders.create(), StompCommand.CONNECTED);
        frame.addHeader(Elements.SESSION, sc.session());
        frame.addHeader(Elements.VERSION, sc.version());
        frame.addHeader(Elements.SERVER, IdGenerator.name());
        frame.addHeader(Elements.HEART_BEAT, HeartBeat.DEFAULT.value());

        sc.send(frame);
    }

    private void doDisconnect(final StompFrame sf, final StompNetChannel sc) {

        doReceipt(sf, sc);
        sc.close();
    }

    private void doNack(final StompFrame sf, final StompNetChannel sc) {

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
        } else {
            if(!Transactions.getInstance().registerTransaction(sc, id)) {
                Transactions.getInstance().removeTransactions(sc);
                sc.send(errorFrame("Failed to transaction.", formatString("Failed to transaction add id = {}", id)));
                sc.close();
                return;
            }

            doReceipt(sf, sc);
        }
    }

    private void doSend(final StompFrame sf, final StompNetChannel sc) {
        String txId = sf.getHeader(Elements.TRANSACTION);
        if (txId != null) {
            Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
            if (transaction == null) {
                sc.send(errorFrame("Unknown transaction", formatString("Unknown transaction id = {}", txId)));
                sc.close();
                return;
            }

            transaction.addFrame(sf);
            doReceipt(sf, sc);
            return;
        }

        send(sf, sc);
        doReceipt(sf, sc);
    }

    private void send(final StompFrame sf, final StompNetChannel sc) {

        String destination = sf.getHeader(Elements.DESTINATION);
        if(destination == null) {
            sc.send(errorFrame("Wrong send.", "Wrong send destination id, Id is required."));
            sc.close();
            return;
        }

        List<DispatcherQueue> dispatch = dispatcher.dispatch(RoutingKey.create(destination));
        dispatch.stream()
                .map(DispatcherQueue::dispatch)
                .forEach(message -> {

                    final StompHeaders headers = StompHeaders.create();
                    String header = sf.getHeader(Elements.ID);
                    if(header == null) {
                        header = "null";
                    }

                    headers.put(Elements.SUBSCRIPTION, header);
                    headers.put(Elements.MESSAGE_ID, message.getUniqueId());

                    final StompFrame frame = create(headers, StompCommand.MESSAGE, message.getBody());
                    sc.subscriptions().forEach(subscription -> {
                            if (!AckMode.AUTO.equals(subscription.ackMode())) {
                                headers.put(Elements.ACK, message.getUniqueId());
                            }

                            subscription.channel().send(frame);
                        }
                    );
                });
    }

    private void doSubscribe(final StompFrame sf, final StompNetChannel sc) {

        String destination = sf.getHeader(Elements.DESTINATION);
        String id = sf.getHeader(Elements.ID);
        String ack = sf.getHeader(Elements.ACK);
        if(ack == null) {
            ack = AckMode.AUTO;
        }

        if(id == null || destination == null) {
            sc.send(errorFrame("Failed to subscribe.", formatString("Failed to subscribe destination = {}",
                    destination == null ? "null" : destination)));
            sc.close();
            return;
        }

        // TODO Enforce a maximum subscriber limit. default size 100.
        final RoutingKey key = RoutingKey.create(destination);
        try {
            sc.registerInboundSubscription(id, destination, ack);
        } catch (Exception e) {
            log.error("Error subscribe id = {} {}", id, e.getMessage());
            sc.send(errorFrame("Failed to subscribe.", formatString("Failed to subscribe destination = {}", destination)));
            sc.close();
            return;
        }

        if(!dispatcher.exists(key)) {
            sc.send(errorFrame("Not found dispatcher.", formatString("Not found dispatcher. destination = {}", destination)));
            sc.close();
            return;
        }

        doReceipt(sf, sc);
    }

    private void doUnsubscribe(final StompFrame sf, final StompNetChannel sc) {

        String id = sf.getHeader(Elements.ID);
        if(id == null) {
            sc.send(errorFrame("Wrong unsubscribe id.", "Wrong unsubscribe id, Id is required."));
            sc.close();
            return;
        }

        try {
            sc.removeInboundSubscription(id);
        } catch (Exception e) {
            log.error("Error unsubscribe id = {} {}", id, e.getMessage());
            sc.send(errorFrame("Failed to unsubscribe.", formatString("Failed to unsubscribe destination = {}", id)));
            sc.close();
            return;
        }

        doReceipt(sf, sc);
    }

    private void doReceipt(final StompFrame sf, final StompNetChannel sc) {

        String receipt = sf.getHeader(Elements.RECEIPT);
        if(receipt != null) {
            StompFrame stompFrame = create(StompHeaders.create(), StompCommand.RECEIPT);
            stompFrame.addHeader(Elements.RECEIPT_ID, receipt);

            sc.send(stompFrame);
        }
    }
}
