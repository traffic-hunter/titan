/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.dispatch.exporter;

import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.channel.stomp.StompServerChannel;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.AggregationResult;
import org.traffichunter.titan.dispatch.SlowConsumerMetrics;

import java.util.List;

/**
 * Dispatch exporter for STOMP subscriptions.
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
public class StompDispatchExporter implements DispatchExporter {

    private final StompServerChannel serverConnection;
    private final SlowConsumerMetrics slowConsumerMetrics;

    public StompDispatchExporter(StompServerChannel serverConnection) {
        this(serverConnection, SlowConsumerMetrics.global());
    }

    StompDispatchExporter(StompServerChannel serverConnection, SlowConsumerMetrics slowConsumerMetrics) {
        this.serverConnection = serverConnection;
        this.slowConsumerMetrics = slowConsumerMetrics;
    }

    @Override
    public String name() {
        return "stomp";
    }

    @Override
    public AggregationResult export(Destination destination, Buffer message) {
        List<StompServerSubscription> subscriptions =
                serverConnection.subscriptions().findByDestination(destination);

        AggregationResult result = AggregationResult.create(
                List.of(destination),
                subscriptions.size()
        );

        subscriptions.forEach(subscription -> {
            StompClientChannel clientChannel = subscription.getConnection();
            if (!clientChannel.channel().isWritable()) {
                slowConsumerMetrics.recordSkippedMessage();
                result.fail();
                return;
            }

            StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.MESSAGE, message.getBytes());
            frame.addHeader(StompHeaders.Elements.DESTINATION, destination.path());
            frame.addHeader(StompHeaders.Elements.SUBSCRIPTION, subscription.id());
            frame.addHeader(StompHeaders.Elements.MESSAGE_ID, IdGenerator.uuid());

            Promise<StompFrame> sendPromise = clientChannel.send(frame);
            sendPromise.addListener(sendFuture -> {
                if (sendFuture.isSuccess()) {
                    result.success();
                } else {
                    result.fail();
                }
            });
        });

        return result;
    }
}
