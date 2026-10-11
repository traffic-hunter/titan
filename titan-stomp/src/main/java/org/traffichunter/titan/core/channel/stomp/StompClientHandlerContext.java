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
package org.traffichunter.titan.core.channel.stomp;

import java.nio.charset.StandardCharsets;
import org.traffichunter.titan.core.codec.stomp.StompException;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.message.DispatchRejectException;
import org.traffichunter.titan.core.message.DispatchResult;

/**
 * Handles protocol responses before application callbacks receive them.
 *
 * @author yun
 */
public final class StompClientHandlerContext {

    /**
     * Fails the request identified by an ERROR frame's receipt ID.
     *
     * <p>Known queue refusal codes in the message header become {@link DispatchRejectException}.
     * Missing, malformed, unknown, and acceptance codes remain general STOMP failures. An ERROR
     * without a receipt ID is still delivered to callbacks but cannot identify a pending request.</p>
     */
    public void failReceipt(StompFrame frame, StompClientChannel connection) {
        String receiptId = frame.getHeader(Elements.RECEIPT_ID);
        if (receiptId == null || receiptId.isBlank()) {
            return;
        }

        String code = frame.getHeader(Elements.MESSAGE);
        DispatchResult result = null;
        if (code != null) {
            try {
                result = DispatchResult.fromCode(Integer.parseInt(code));
            } catch (NumberFormatException ignored) {
                // Other STOMP servers can use descriptive text in the message header.
            }
        }

        String description = new String(frame.body(), StandardCharsets.UTF_8);
        if (description.isBlank()) {
            description = code == null || code.isBlank() ? "Received ERROR frame from server" : code;
        }
        RuntimeException error = result != null && result != DispatchResult.ACCEPTED
                ? new DispatchRejectException(result, description) : new StompException(description);
        connection.failReceipt(receiptId, error);
    }
}
