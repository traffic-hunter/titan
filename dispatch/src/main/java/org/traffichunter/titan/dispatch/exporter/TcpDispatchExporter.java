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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Dispatch exporter that writes raw payload buffers to every active TCP child
 * channel owned by an {@link InetServer}.
 *
 * <p>Every active child channel receives the payload, regardless of protocol subscriptions.
 * Use a protocol-aware exporter such as {@link StompDispatchExporter} to send only to
 * channels with matching subscriptions.</p>
 *
 * <p>Raw TCP has no way to express a destination group, so this exporter serves the default
 * group only and refuses anything else rather than handing one group's messages to every
 * channel it knows.</p>
 */
public class TcpDispatchExporter implements DispatchExporter {

    private static final Logger log = LoggerFactory.getLogger(TcpDispatchExporter.class);

    private final InetServer inetServer;

    public TcpDispatchExporter(InetServer inetServer) {
        this.inetServer = Assert.checkNotNull(inetServer, "inetServer");
    }

    @Override
    public String name() {
        return "inet";
    }

    @Override
    public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
        Assert.checkState(inetServer.isStarted(), "Cannot send an unstarted inet server");
        if (!DestinationGroups.isDefault(group)) {
            throw new UnsupportedOperationException(
                    "The inet exporter cannot keep destination group " + group + " to itself");
        }

        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (NetChannel channel : inetServer.childChannel().stream().toList()) {
            if (!channel.isActive() || channel.isClosed()) {
                continue;
            }

            Buffer copiedPayload = payload.copy();
            try {
                writes.add(channel.writeAndFlush(copiedPayload)
                        .toCompletableFuture()
                        .handle((ignored, ignoredError) -> null));
            } catch (Exception e) {
                // One unusable channel must not stop the fanout to the others.
                copiedPayload.release();
                log.warn("Failed to write a fanout payload. channelId={}", channel.id(), e);
            }
        }

        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
}
