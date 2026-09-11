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

import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.AggregationResult;

import java.util.List;

/**
 * Dispatch exporter that writes raw payload buffers to every active TCP child
 * channel owned by an {@link InetServer}.
 *
 * <p>Every active child channel receives the payload, regardless of protocol subscriptions.
 * Use a protocol-aware exporter such as {@link StompDispatchExporter} to send only to
 * channels with matching subscriptions.</p>
 */
public class TcpDispatchExporter implements DispatchExporter {

    private final InetServer inetServer;

    public TcpDispatchExporter(InetServer inetServer) {
        this.inetServer = Assert.checkNotNull(inetServer, "inetServer");
    }

    @Override
    public String name() {
        return "inet";
    }

    @Override
    public AggregationResult export(String group, Destination destination, Buffer payload) {
        Assert.checkState(inetServer.isStarted(), "Cannot send an unstarted inet server");

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        for (NetChannel channel : inetServer.childChannel().stream().toList()) {
            if (!channel.isActive() || channel.isClosed()) {
                continue;
            }

            attempted++;
            Buffer copiedPayload = payload.copy();
            try {
                channel.writeAndFlush(copiedPayload);
                succeeded++;
            } catch (Exception e) {
                copiedPayload.release();
                failed++;
            }
        }

        return AggregationResult.completed(
                List.of(destination),
                attempted,
                succeeded,
                failed
        );
    }
}
