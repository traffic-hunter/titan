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

import java.util.concurrent.CompletionStage;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.Frame;

/**
 * Writes a fanout payload to subscribed clients using their protocol.
 *
 * <p>The gateway calls exporters after a message has been routed to a
 * destination queue. Implementations should find the currently eligible
 * consumers for the destination inside the given group and write the payload to
 * each of them. Subscribers of the same destination in another group must not
 * receive the payload.</p>
 *
 * <p>Returning does not mean the payload reached anyone. The returned stage completes when
 * every write this export started has finished, whether or not it succeeded, and it carries no
 * result. A consumer the exporter could not write to is not waited for.</p>
 *
 * <p>Not every protocol can express a group. An implementation whose subscribers are resolved by
 * destination alone throws {@link UnsupportedOperationException} for a group other than
 * {@code default} instead of delivering the payload to all of them.</p>
 *
 * @author yun
 */
public interface DispatchExporter {

    String name();

    default CompletionStage<@Nullable Void> export(String group, Destination destination, Frame<?, ?> payload) {
        return exportOwned(group, destination, payload.toBuffer());
    }

    default CompletionStage<@Nullable Void> export(String group, Destination destination, Message payload) {
        return exportOwned(group, destination, Buffer.heap().alloc(payload.getBody()));
    }

    /**
     * Exports a borrowed payload buffer.
     *
     * <p>The buffer stays valid until the returned stage completes, so an implementation that is
     * still writing when it returns can keep using the payload without retaining it. The caller
     * owns the buffer and releases it once.</p>
     */
    CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload);

    /** Exports a buffer allocated here and releases it when the export finishes. */
    private CompletionStage<@Nullable Void> exportOwned(
            String group,
            Destination destination,
            Buffer payload
    ) {
        CompletionStage<@Nullable Void> completion;
        try {
            completion = export(group, destination, payload);
        } catch (RuntimeException error) {
            payload.release();
            throw error;
        }

        return completion.whenComplete((ignored, ignoredError) -> payload.release());
    }
}
