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
import org.traffichunter.titan.core.util.Assert;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.dispatch.AggregationResult;

import java.util.List;

/**
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
    public AggregationResult export(String group, Destination destination, Buffer payload) {
        Assert.checkState(server.isListening(), "Vert.x STOMP server is not listening");

        io.vertx.ext.stomp.Destination stompDestination = server.stompHandler()
                .getDestination(destination.path());
        if (stompDestination == null) {
            return AggregationResult.completed(List.of(destination), 0, 0, 0);
        }

        int attempted = stompDestination.numberOfSubscriptions();
        int succeeded = 0;
        int failed = 0;

        try {
            Frame frame = new Frame()
                    .setCommand(Command.MESSAGE)
                    .setDestination(destination.path())
                    .setBody(io.vertx.core.buffer.Buffer.buffer(payload.getBytes()));
            frame.addHeader(Frame.DESTINATION, destination.path());
            frame.addHeader(Frame.MESSAGE_ID, IdGenerator.uuid());
            frame.addHeader(Frame.CONTENT_LENGTH, Integer.toString(payload.length()));
            stompDestination.dispatch(null, frame);
            succeeded = attempted;
        } catch (Exception e) {
            failed = attempted;
        }

        return AggregationResult.completed(
                List.of(destination),
                attempted,
                succeeded,
                failed
        );
    }
}
