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
 * Fanout exporter for STOMP subscriptions.
 *
 * <p>The exporter asks the server connection for subscriptions matching the
 * destination, then emits a STOMP {@code MESSAGE} frame per subscription. The
 * {@code subscription} header is copied from the subscription id owned by that
 * client session, which lets a single STOMP connection multiplex multiple
 * subscriptions correctly.</p>
 *
 * <p>Each outgoing frame receives a copied payload buffer because the same
 * logical message can be written to many clients. Sharing one buffer instance
 * across those writes would couple independent channel write lifecycles.</p>
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
