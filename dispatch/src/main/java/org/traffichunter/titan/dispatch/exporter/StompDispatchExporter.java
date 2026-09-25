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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.channel.stomp.StompServerChannel;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.SlowConsumerMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.time.Duration;

/**
 * Dispatch exporter for STOMP subscriptions.
 *
 * <p>The exporter asks the server connection for subscriptions matching the
 * destination within the group, then emits a STOMP {@code MESSAGE} frame per
 * subscription. The {@code subscription} header is copied from the subscription
 * id owned by that client session, which lets a single STOMP connection
 * multiplex multiple subscriptions correctly. The {@code group} header is added
 * only for groups other than the default, so clients that never send the header
 * never see it.</p>
 *
 * <p>Each outgoing frame receives a copied payload buffer because the same
 * logical message can be written to many clients. Sharing one buffer instance
 * across those writes would couple independent channel write lifecycles.</p>
 *
 * <p>Whether a subscriber can take the write is decided on that connection's own event loop,
 * right before the write, so no flush or other writer can change the answer in between. The
 * returned stage completes when every subscriber has been handed a frame or skipped. Socket
 * writes may finish later.</p>
 *
 * @author yun
 */
public class StompDispatchExporter implements DispatchExporter {

    /** An attempt that has waited longer than this for its event loop skips the send. */
    private static final Duration EXPORT_TIMEOUT = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(StompDispatchExporter.class);

    private final StompServerChannel serverConnection;
    private final SlowConsumerMetrics slowConsumerMetrics;
    private final long exportTimeoutNanos;

    public StompDispatchExporter(StompServerChannel serverConnection) {
        this(serverConnection, SlowConsumerMetrics.global());
    }

    StompDispatchExporter(StompServerChannel serverConnection, SlowConsumerMetrics slowConsumerMetrics) {
        this(serverConnection, slowConsumerMetrics, EXPORT_TIMEOUT);
    }

    StompDispatchExporter(
            StompServerChannel serverConnection,
            SlowConsumerMetrics slowConsumerMetrics,
            Duration exportTimeout
    ) {
        this.serverConnection = serverConnection;
        this.slowConsumerMetrics = slowConsumerMetrics;
        this.exportTimeoutNanos = exportTimeout.toNanos();
    }

    @Override
    public String name() {
        return "stomp";
    }

    @Override
    public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer message) {
        long deadlineNanos = System.nanoTime() + exportTimeoutNanos;
        List<StompServerSubscription> subscriptions =
                serverConnection.subscriptions().findByDestination(group, destination);

        // The caller may release the message as soon as this returns, and the loop hops outlive it.
        byte[] body = message.getBytes();

        List<CompletableFuture<?>> writes = new ArrayList<>(subscriptions.size());
        for (StompServerSubscription subscription : subscriptions) {
            writes.add(export(group, destination, subscription, body, deadlineNanos));
        }

        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<@Nullable Void> export(
            String group,
            Destination destination,
            StompServerSubscription subscription,
            byte[] body,
            long deadlineNanos
    ) {
        StompClientChannel clientChannel = subscription.getConnection();
        NetChannel channel = clientChannel.channel();
        CompletableFuture<@Nullable Void> result = new CompletableFuture<>();
        Runnable attempt = () -> {
            try {
                if (System.nanoTime() - deadlineNanos >= 0) {
                    slowConsumerMetrics.recordSkippedMessage();
                    return;
                }
                if (!channel.isWritable()) {
                    slowConsumerMetrics.recordSkippedMessage();
                    return;
                }

                StompFrame frame = StompFrame.create(StompHeaders.create(), StompCommand.MESSAGE, body);
                frame.addHeader(StompHeaders.Elements.DESTINATION, destination.path());
                frame.addHeader(StompHeaders.Elements.SUBSCRIPTION, subscription.id());
                frame.addHeader(StompHeaders.Elements.MESSAGE_ID, IdGenerator.uuid());
                if (!DestinationGroups.isDefault(group)) {
                    frame.addHeader(StompHeaders.Elements.GROUP, group);
                }

                // Socket drain is the connection's own pace and must not hold the next queue message.
                if (System.nanoTime() - deadlineNanos >= 0) {
                    slowConsumerMetrics.recordSkippedMessage();
                    return;
                }
                clientChannel.send(frame);
            } catch (RuntimeException error) {
                log.warn("Failed to hand STOMP frame to subscriber. destination={}, subscription={}",
                        destination.path(), subscription.id(), error);
            } finally {
                result.complete(null);
            }
        };

        try {
            channel.eventLoop().execute(attempt);
        } catch (RejectedExecutionException e) {
            // The loop is shutting down, which leaves the subscriber as unreachable as a closed one.
            result.complete(null);
        }
        return result;
    }
}
