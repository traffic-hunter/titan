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
package org.traffichunter.titan.fanout.exporter;

import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.transport.stomp.StompServer;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.FanoutResult;

import java.util.List;

/**
 * @author yun
 */
public class StompServerFanoutExporter implements FanoutExporter {

    private final StompServer stompServer;

    public StompServerFanoutExporter(StompServer stompServer) {
        this.stompServer = stompServer;
    }

    @Override
    public String name() {
        return "stomp";
    }

    @Override
    public FanoutResult send(Destination destination, Buffer message) {
        Assert.checkState(stompServer.isStart(), "Cannot send an unstarted stomp server");

        List<StompServerSubscription> subscriptions = stompServer.connection()
                .subscriptions()
                .findByDestination(destination);

        int success = 0;
        int failure = 0;
        for(StompServerSubscription subscription : subscriptions) {
            Buffer payload = message.copy();
            try {
                subscription.getConnection()
                        .channel()
                        .writeAndFlush(payload);
                success++;
            } catch (Exception e) {
                payload.release();
                failure++;
            }
        }

        return new FanoutResult(destination, subscriptions.size(), success, failure);
    }

    @Override
    public boolean isOpen() {
        return stompServer.isStart();
    }

    @Override
    public boolean isClose() {
        return stompServer.isShutdown();
    }

    @Override
    public void close() {
        stompServer.shutdown();
    }
}
