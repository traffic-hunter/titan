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

import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.channel.stomp.StompServerConnection;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.fanout.CompletableResult;

import java.util.List;

/**
 * @author yun
 */
public class StompFanoutExporter implements FanoutExporter {

    private final StompServerConnection serverConnection;

    public StompFanoutExporter(StompServerConnection serverConnection) {
        this.serverConnection = serverConnection;
    }

    @Override
    public String name() {
        return "stomp";
    }

    @Override
    public CompletableResult export(Destination destination, Buffer message) {
        List<StompServerSubscription> subscriptions =
                serverConnection.subscriptions().findByDestination(destination);

        Promise<CompletableResult> resultPromise = Promise.newPromise(serverConnection.channel().eventLoop());
        CompletableResult result = CompletableResult.create(
                List.of(destination),
                subscriptions.size(),
                resultPromise
        );

        subscriptions.forEach(subscription -> {
            StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.MESSAGE, message.copy());
            frame.addHeader(StompHeaders.Elements.DESTINATION, destination.path());
            frame.addHeader(StompHeaders.Elements.SUBSCRIPTION, subscription.id());
            frame.addHeader(StompHeaders.Elements.MESSAGE_ID, IdGenerator.uuid());

            Promise<StompFrame> sendPromise = subscription.getConnection().send(frame);
            sendPromise.addListener(sendFuture -> {
                if (sendFuture.isSuccess()) {
                    result.markSuccess();
                } else {
                    result.markFailure();
                }
            });
        });

        return result;
    }
}
