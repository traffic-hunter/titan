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
package org.traffichunter.titan.core.codec.stomp;

import static org.traffichunter.titan.core.codec.stomp.StompVersion.*;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.codec.Json;
import org.traffichunter.titan.core.codec.stomp.StompFrame.AckMode;
import org.traffichunter.titan.core.codec.stomp.StompFrame.HeartBeat;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.dispatcher.Dispatcher;
import org.traffichunter.titan.core.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.transport.stomp.StompServerChannel;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.RoutingKey;

/**
 * @author yungwang-o
 */
@Slf4j
public final class StompHandlerImpl implements StompHandler {

    private final Dispatcher dispatcher;

    public StompHandlerImpl(final Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void handle(final StompFrame sf, final StompServerChannel sc) {
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
            default -> throw new StompException("Unknown command: " + sf.getCommand());
        }
    }

    private void doBegin(final StompFrame sf, final StompServerChannel sc) {

        String txId = sf.getHeader(Elements.TRANSACTION);
        if (txId == null) {
            sc.send(StompFrame.errorFrame("Transaction id is required."));
            sc.close();
            return;
        }

        if(Transactions.getInstance().registerTransaction(sc, txId)) {
            sc.send(StompFrame.errorFrame("Transaction id = {} already exists.", txId));
            sc.close();
            return;
        }

        doReceipt(sf, sc);
    }

    private void doAbort(final StompFrame sf, final StompServerChannel sc) {

        String txId = sf.getHeader(Elements.TRANSACTION);
        if (txId == null) {
            sc.send(StompFrame.errorFrame("Transaction id is required."));
            sc.close();
            return;
        }

        if (!Transactions.getInstance().removeTransaction(sc, txId)) {
            sc.send(StompFrame.errorFrame("Unknown transaction.", txId));
            sc.close();
            return;
        }

        doReceipt(sf, sc);
    }

    private void doAck(final StompFrame sf, final StompServerChannel sc) {

        String id = sf.getHeader(Elements.ID);
        if(id == null) {
            sc.send(StompFrame.errorFrame("Wrong ack id, Id is required."));
            sc.close();
            return;
        }

        String txId = sf.getHeader(Elements.TRANSACTION);
        if(txId != null) {
            Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
            if(transaction == null) {
                sc.send(StompFrame.errorFrame("Unknown transaction id = {}", txId));
                sc.close();
            }
        } else {
            if(Transactions.getInstance().registerTransaction(sc, id)) {
                Transactions.getInstance().removeTransactions(sc);
                sc.send(StompFrame.errorFrame("Failed transaction add id = {}", id));
                sc.close();
                return;
            }

            doReceipt(sf, sc);
        }
    }

    private void doCommit(final StompFrame sf, final StompServerChannel sc) {

        String txId = sf.getHeader(Elements.TRANSACTION);
        if(txId == null) {
            sc.send(StompFrame.errorFrame("Transaction id is required."));
            sc.close();
            return;
        }

        Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
        if(transaction == null) {
            sc.send(StompFrame.errorFrame("Unknown transaction id = {}", txId));
            sc.close();
            return;
        }

        // TODO tx chunk implementation.

        transaction.clear();
        Transactions.getInstance().removeTransaction(sc, txId);

        doReceipt(sf, sc);
    }

    private void doConnect(final StompFrame sf, final StompServerChannel sc) {

        String accept = Optional.ofNullable(sf.getHeader(Elements.ACCEPT_VERSION))
                .orElse(STOMP_1_0.getVersion());

        // negotiate
        if(!accept.equals(sc.server().getVersion())) {
            sc.send(StompFrame.errorFrame("Not match stomp version... client version = {}", accept));
            sc.close();
            return;
        }

        String heartbeat = Optional.ofNullable(sf.getHeader(Elements.HEART_BEAT))
                .orElse(Json.serialize(HeartBeat.doParse(null)));
        long ping = StompFrame.HeartBeat.computePingClientToServer(
                HeartBeat.DEFAULT,
                HeartBeat.doParse(heartbeat)
        );
        long pong = StompFrame.HeartBeat.computePingClientToServer(
                HeartBeat.DEFAULT,
                HeartBeat.doParse(heartbeat)
        );

        sc.setHeartbeat(ping, pong, () -> sc.send(StompFrame.PING));

        StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.CONNECT);
        frame.addHeader(Elements.SESSION, sc.session());
        frame.addHeader(Elements.VERSION, sc.server().getVersion());
        frame.addHeader(Elements.SERVER, IdGenerator.name());
        frame.addHeader(Elements.HEART_BEAT, Json.serialize(HeartBeat.DEFAULT));

        sc.send(frame.toBuffer());
    }

    private void doDisconnect(final StompFrame sf, final StompServerChannel sc) {

        doReceipt(sf, sc);
        sc.close();
    }

    private void doNack(final StompFrame sf, final StompServerChannel sc) {

        String id = sf.getHeader(Elements.ID);
        if(id == null) {
            sc.send(StompFrame.errorFrame("Wrong nack id, Id is required."));
            sc.close();
            return;
        }

        String txId = sf.getHeader(Elements.TRANSACTION);
        if(txId != null) {
            Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
            if(transaction == null) {
                sc.send(StompFrame.errorFrame("Unknown transaction id = {}", txId));
                sc.close();
            }
        } else {
            if(Transactions.getInstance().registerTransaction(sc, id)) {
                Transactions.getInstance().removeTransactions(sc);
                sc.send(StompFrame.errorFrame("Failed transaction add id = {}", id));
                sc.close();
                return;
            }

            doReceipt(sf, sc);
        }
    }

    private void doSend(final StompFrame sf, final StompServerChannel sc) {

        String destination = sf.getHeader(Elements.DESTINATION);
        if(destination == null) {
            sc.send(StompFrame.errorFrame("Wrong send destination id, Id is required."));
            sc.close();
            return;
        }

        List<DispatcherQueue> dispatch = dispatcher.dispatch(RoutingKey.create(destination));
        dispatch.stream()
                .map(DispatcherQueue::dispatch)
                .forEach(message -> {

                    final StompHeaders headers = StompHeaders.create();
                    headers.put(Elements.SUBSCRIPTION, sf.getHeader(Elements.ID));
                    headers.put(Elements.MESSAGE_ID, message.getUniqueId());

                    final StompFrame frame = StompFrame.create(headers, StompCommand.MESSAGE, message.getBody());
                    sc.subscriptions().forEach(subscription -> {
                            if (!subscription.ackMode().equals(AckMode.AUTO)) {
                                headers.put(Elements.ACK, message.getUniqueId());
                            }

                            //subscription.channel().send(frame.toBuffer());
                        }
                    );
                });

        doReceipt(sf, sc);
    }

    private void doSubscribe(final StompFrame sf, final StompServerChannel sc) {

        String destination = sf.getHeader(Elements.DESTINATION);
        String id = sf.getHeader(Elements.ID);
        String ack = sf.getHeader(Elements.ACK);
        if(ack == null) {
            ack = AckMode.AUTO;
        }

        if(id == null || destination == null) {
            sc.send(StompFrame.errorFrame("Failed subscribe destination = {}", destination));
            sc.close();
            return;
        }

        // TODO Enforce a maximum subscriber limit. default size 100.
        final RoutingKey key = RoutingKey.create(destination);
        try {
            ServerSubscription subscription = ServerSubscription.builder()
                    .id(id)
                    .ackMode(ack)
                //    .channel(sc)
                    .key(key)
                    .build();

            sc.subscribe(id, subscription);
        } catch (Exception e) {
            log.error("Error subscribe id = {} {}", id, e.getMessage());
            sc.send(StompFrame.errorFrame("Failed to subscribe destination = {}", destination));
            sc.close();
            return;
        }

        if(!dispatcher.exists(key)) {
            sc.send(StompFrame.errorFrame("Not found dispatcher. destination = {}", destination));
            sc.close();
            return;
        }

        doReceipt(sf, sc);
    }

    private void doUnsubscribe(final StompFrame sf, final StompServerChannel sc) {

        String id = sf.getHeader(Elements.ID);
        if(id == null) {
            sc.send(StompFrame.errorFrame("Wrong unsubscribe id, Id is required."));
            sc.close();
            return;
        }

        try {
            sc.unsubscribe(id);
        } catch (Exception e) {
            log.error("Error unsubscribe id = {} {}", id, e.getMessage());
            sc.send(StompFrame.errorFrame("Failed to unsubscribe destination = {}", id));
            sc.close();
            return;
        }

        doReceipt(sf, sc);
    }

    private void doReceipt(final StompFrame sf, final StompServerChannel sc) {

        String receipt = sf.getHeader(Elements.RECEIPT);
        if(receipt != null) {
            StompFrame stompFrame = StompFrame.create(StompHeaders.create(), StompCommand.RECEIPT);
            stompFrame.addHeader(Elements.RECEIPT_ID, receipt);

            sc.send(stompFrame);
        }
    }
}
