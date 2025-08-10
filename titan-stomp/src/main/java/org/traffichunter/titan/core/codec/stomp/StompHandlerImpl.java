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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.dispatcher.Dispatcher;
import org.traffichunter.titan.core.transport.stomp.StompServerConnection;
import org.traffichunter.titan.servicediscovery.ServiceDiscovery;

/**
 * @author yungwang-o
 */
public final class StompHandlerImpl implements StompHandler {

    private final ServiceDiscovery serviceDiscovery;
    private final Dispatcher dispatcher;

    public StompHandlerImpl(final ServiceDiscovery serviceDiscovery,
                            final Dispatcher dispatcher) {

        this.serviceDiscovery = serviceDiscovery;
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

    }

    private void doAbort(final StompFrame sf, final StompServerConnection sc) {

    }

    private void doAck(final StompFrame sf, final StompServerConnection sc) {

    }

    private void doCommit(final StompFrame sf, final StompServerConnection sc) {

    }

    private void doConnect(final StompFrame sf, final StompServerConnection sc) {

        String accept = sf.getHeader(Elements.ACCEPT_VERSION);
        if(accept == null) {
            accept = STOMP_1_0.getVersion();
        }

        // negotiate
        if(!accept.equals(sc.server().getVersion())) {
            sc.write(ByteBuffer.wrap(
                    StompFrame.errorFrame("Not match stomp version... client version = {}", accept)
                            .getBytes(StandardCharsets.UTF_8))
            );
            sc.close();
        }
    }

    private void doDisconnect(final StompFrame sf, final StompServerConnection sc) {
        doReceipt(sf, sc);
        sc.close();
    }

    private void doNack(final StompFrame sf, final StompServerConnection sc) {

    }

    private void doSend(final StompFrame sf, final StompServerConnection sc) {

    }

    private void doSubscribe(final StompFrame sf, final StompServerConnection sc) {

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
