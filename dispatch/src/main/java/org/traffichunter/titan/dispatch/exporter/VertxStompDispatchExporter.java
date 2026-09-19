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

import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Dispatch exporter that hands a payload to the Vert.x STOMP server's own destination.
 *
 * <p>Vert.x resolves subscribers by path alone, so it cannot tell two groups holding one
 * destination apart. This exporter therefore serves the default group only and refuses anything
 * else, matching the Vert.x SEND handler, which rejects a {@code group} header outright.</p>
 *
 * @author yun
 */
public final class VertxStompDispatchExporter implements DispatchExporter {

    private final StompServer server;

    public VertxStompDispatchExporter(StompServer server) {
        this.server = Assert.checkNotNull(server, "server");
    }

    @Override
    public String name() {
        return "vertx-stomp";
    }

    @Override
    public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
        Assert.checkState(server.isListening(), "Vert.x STOMP server is not listening");
        if (!DestinationGroups.isDefault(group)) {
            throw new UnsupportedOperationException(
                    "The Vert.x STOMP exporter cannot keep destination group " + group + " to itself");
        }

        io.vertx.ext.stomp.Destination stompDestination = server.stompHandler()
                .getDestination(destination.path());
        if (stompDestination == null) {
            return CompletableFuture.completedFuture(null);
        }

        Frame frame = new Frame()
                .setCommand(Command.MESSAGE)
                .setDestination(destination.path())
                .setBody(io.vertx.core.buffer.Buffer.buffer(payload.getBytes()));
        frame.addHeader(Frame.DESTINATION, destination.path());
        frame.addHeader(Frame.MESSAGE_ID, IdGenerator.uuid());
        frame.addHeader(Frame.CONTENT_LENGTH, Integer.toString(payload.length()));
        // Vert.x owns the write from here and reports nothing back, so there is nothing to wait for.
        stompDestination.dispatch(null, frame);
        return CompletableFuture.completedFuture(null);
    }
}
