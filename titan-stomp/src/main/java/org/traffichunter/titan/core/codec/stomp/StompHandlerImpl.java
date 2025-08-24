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

import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import org.traffichunter.titan.core.codec.stomp.StompFrame.AckMode;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.dispatcher.Dispatcher;
import org.traffichunter.titan.core.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.servicediscovery.RouteStatus;
import org.traffichunter.titan.core.servicediscovery.Subscription;
import org.traffichunter.titan.core.transport.stomp.StompServerConnection;
import org.traffichunter.titan.core.util.RoutingKey;

/**
 * @author yungwang-o
 */
public final class StompHandlerImpl implements StompHandler {

    private final Dispatcher dispatcher;

    public StompHandlerImpl(final Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void handle(final StompFrame sf, final StompServerConnection sc) {

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

    private void doBegin(final StompFrame sf, final StompServerConnection sc) {

        String txId = sf.getHeader(Elements.TRANSACTION);
        if (txId == null) {
            sc.write(StompFrame.errorFrame("Transaction id is required."));
            sc.close();
            return;
        }

        if(!Transactions.getInstance().addTransaction(sc, txId)) {
            sc.write(StompFrame.errorFrame("Transaction id = {} already exists.", txId));
            sc.close();
            return;
        }

        doReceipt(sf, sc);
    }

    private void doAbort(final StompFrame sf, final StompServerConnection sc) {

        String txId = sf.getHeader(Elements.TRANSACTION);
        if (txId == null) {
            sc.write(StompFrame.errorFrame("Transaction id is required."));
            sc.close();
            return;
        }

        if (!Transactions.getInstance().removeTransaction(sc, txId)) {
            sc.write(StompFrame.errorFrame("Unknown transaction.", txId));
            sc.close();
            return;
        }

        doReceipt(sf, sc);
    }

    private void doAck(final StompFrame sf, final StompServerConnection sc) {

        String id = sf.getHeader(Elements.ID);
        if(id == null) {
            sc.write(StompFrame.errorFrame("Wrong ack id, Id is required."));
            sc.close();
            return;
        }

        String txId = sf.getHeader(Elements.TRANSACTION);
        if(txId != null) {
            Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
            if(transaction == null) {
                sc.write(StompFrame.errorFrame("Unknown transaction id = {}", txId));
                sc.close();
            }
        } else {
            if(!Transactions.getInstance().addTransaction(sc, id)) {
                Transactions.getInstance().removeTransactions(sc);
                sc.write(StompFrame.errorFrame("Failed transaction add id = {}", id));
                sc.close();
                return;
            }

            doReceipt(sf, sc);
        }
    }

    private void doCommit(final StompFrame sf, final StompServerConnection sc) {

        String txId = sf.getHeader(Elements.TRANSACTION);
        if(txId == null) {
            sc.write(StompFrame.errorFrame("Transaction id is required."));
            sc.close();
            return;
        }

        Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
        if(transaction == null) {
            sc.write(StompFrame.errorFrame("Unknown transaction id = {}", txId));
            sc.close();
            return;
        }



        transaction.clear();
        Transactions.getInstance().removeTransaction(sc, txId);

        doReceipt(sf, sc);
    }

    private void doConnect(final StompFrame sf, final StompServerConnection sc) {

        String accept = Optional.ofNullable(sf.getHeader(Elements.ACCEPT_VERSION))
                .orElse(STOMP_1_0.getVersion());

        // negotiate
        if(!accept.equals(sc.server().getVersion())) {
            sc.write(StompFrame.errorFrame("Not match stomp version... client version = {}", accept));
            sc.close();
        }
    }

    private void doDisconnect(final StompFrame sf, final StompServerConnection sc) {

        doReceipt(sf, sc);
        sc.close();
    }

    private void doNack(final StompFrame sf, final StompServerConnection sc) {

        String id = sf.getHeader(Elements.ID);
        if(id == null) {
            sc.write(StompFrame.errorFrame("Wrong nack id, Id is required."));
            sc.close();
            return;
        }

        String txId = sf.getHeader(Elements.TRANSACTION);
        if(txId != null) {
            Transaction transaction = Transactions.getInstance().getTransaction(sc, txId);
            if(transaction == null) {
                sc.write(StompFrame.errorFrame("Unknown transaction id = {}", txId));
                sc.close();
            }
        } else {
            if(!Transactions.getInstance().addTransaction(sc, id)) {
                Transactions.getInstance().removeTransactions(sc);
                sc.write(StompFrame.errorFrame("Failed transaction add id = {}", id));
                sc.close();
                return;
            }

            doReceipt(sf, sc);
        }
    }

    private void doSend(final StompFrame sf, final StompServerConnection sc) {

    }

    private void doSubscribe(final StompFrame sf, final StompServerConnection sc) {

        String destination = sf.getHeader(Elements.DESTINATION);
        String id = sf.getHeader(Elements.ID);
        String ack = sf.getHeader(Elements.ACK);
        if(ack == null) {
            ack = AckMode.AUTO;
        }

        if(id == null || destination == null) {
            sc.write(StompFrame.errorFrame("Failed subscribe destination = {}", destination));
            sc.close();
            return;
        }

        // TODO Enforce a maximum subscriber limit.

        RoutingKey routingKey = RoutingKey.create(destination);
        if(dispatcher.exists(routingKey)) {
            final Subscription subscription = Subscription.builder()
                    .routeStatus(RouteStatus.UP)
                    .serviceId(id)
                    .serviceName("")
                    .build();

            dispatcher.find(routingKey)
                    .serviceDiscovery()
                    .register(routingKey, subscription);
        } else {
            return;
        }

        doReceipt(sf, sc);
    }

    private void doUnsubscribe(final StompFrame sf, final StompServerConnection sc) {

    }

    private void doReceipt(final StompFrame sf, final StompServerConnection sc) {

        String receipt = sf.getHeader(Elements.RECEIPT);
        if(receipt != null) {
            StompFrame stompFrame = StompFrame.create(StompHeaders.DEFAULT, StompCommand.RECEIPT);
            stompFrame.addHeader(Elements.RECEIPT_ID, receipt);

            sc.write(stompFrame);
        }
    }
}
