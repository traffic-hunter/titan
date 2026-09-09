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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.Frame;
import org.traffichunter.titan.dispatch.AggregationResult;

/**
 * Writes a fanout payload to subscribed clients using their protocol.
 *
 * <p>The gateway calls exporters after a message has been routed to a
 * destination queue. Implementations should find the currently eligible
 * consumers for the destination and return a {@link AggregationResult} that
 * reports how many writes were attempted and completed.</p>
 */
public interface DispatchExporter {

    String name();

    @CanIgnoreReturnValue
    default AggregationResult export(Destination destination, Frame<?, ?> payload) {
        Buffer buffer = payload.toBuffer();
        try {
            return export(destination, buffer);
        } finally {
            buffer.release();
        }
    }

    @CanIgnoreReturnValue
    default AggregationResult export(Destination destination, Message payload) {
        Buffer buffer = Buffer.heap().alloc(payload.getBody());
        try {
            return export(destination, buffer);
        } finally {
            buffer.release();
        }
    }

    /**
     * Exports a borrowed payload buffer.
     *
     * <p>The buffer is valid only for the duration of this invocation. Implementations that
     * retain the payload asynchronously must copy or retain it and release that reference when
     * delivery completes.</p>
     */
    @CanIgnoreReturnValue
    AggregationResult export(Destination destination, Buffer payload);
}
