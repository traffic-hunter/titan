/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.channel.stomp;

import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompFrame.HeartBeat;
import org.traffichunter.titan.core.codec.stomp.StompException;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * @author yun
 */
public class StompClientHandler implements StompHandler {

    @Override
    public void handle(StompFrame sf, StompClientConnection sc) {
        sc.setLastActivatedAt();

        switch (sf.getCommand()) {
            case CONNECTED -> doConnected(sf, sc);
            case MESSAGE -> doMessage(sf, sc);
            case RECEIPT -> doReceipt(sf, sc);
            case ERROR -> doError(sf, sc);
            case PING -> doNothing();
            default -> throw new IllegalStateException("Unexpected value: " + sf.getCommand());
        }
    }

    private void doConnected(StompFrame sf, StompClientConnection sc) {
        sc.connected();

        String heartbeat = sf.getHeader(Elements.HEART_BEAT);
        if (heartbeat != null) {
            HeartBeat server = HeartBeat.doParse(heartbeat);
            long ping = HeartBeat.computePingClientToServer(HeartBeat.DEFAULT, server);
            long pong = HeartBeat.computePongServerToClient(HeartBeat.DEFAULT, server);
            sc.setHeartbeat(ping, pong, () -> sc.send(StompFrame.PING));
        }
    }

    private void doMessage(StompFrame sf, StompClientConnection sc) {
        String id = sf.getHeader(Elements.SUBSCRIPTION);
        sc.subscriptions()
                .stream()
                .filter(subscription -> subscription.id().equals(id))
                .forEach(subscription -> subscription.handler().handle(sf));
    }

    private void doReceipt(StompFrame sf, StompClientConnection sc) {
        String header = sf.getHeader(Elements.RECEIPT_ID);
        if (header != null) {
            sc.receipt(header);
        }
    }

    private void doError(StompFrame sf, StompClientConnection sc) {
        sc.failConnect(new StompException("Received ERROR frame from server"));
        sc.error(sf);
    }

    /**
     * No operation.
     */
    private void doNothing() { }
}
