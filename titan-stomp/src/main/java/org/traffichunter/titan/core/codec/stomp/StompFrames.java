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
package org.traffichunter.titan.core.codec.stomp;

import org.jspecify.annotations.Nullable;
import java.util.Map;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * Public view of a STOMP frame.
 *
 * <p>A frame exposes its command, headers, and body. Delivery confirmation is outside this
 * interface.</p>
 *
 * @author yun
 */
public interface StompFrames {

    static StompFrames from(StompFrame frame) {
        return frame;
    }

    /**
     * The command in this frame.
     *
     * @return the frame command
     */
    StompCommand command();

    /**
     * Looks up a known header.
     *
     * @param key header to look up
     * @return the value, or {@code null} when the header is absent
     */
    @Nullable String getHeader(final Elements key);

    /**
     * An unmodifiable snapshot of the known headers.
     *
     * @return frame headers keyed by their STOMP elements
     */
    Map<Elements, String> headers();

    /**
     * The frame body as bytes.
     *
     * @return body bytes, or an empty array for a frame without a body
     */
    byte[] body();
}
