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
package org.traffichunter.titan.springframework.stomp.messaging;

import org.jspecify.annotations.NullMarked;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.traffichunter.titan.core.codec.stomp.StompFrames;

/**
 * Converts Titan STOMP frames into Spring messaging messages.
 * Copies STOMP headers into Spring headers and keeps the original frame.
 * Used by listener containers before method argument resolution.
 *
 * @author yun
 */
@NullMarked
public final class TitanSpringMessageAdapter {

    public static final String HDR_STOMP_FRAME = "titan.stomp.frame";

    public static Message<byte[]> from(StompFrames frame) {
        MessageBuilder<byte[]> builder = MessageBuilder.withPayload(frame.body());

        frame.headers()
                .forEach((key, value) -> builder.setHeader(key.getName(), value));

        builder.setHeader(HDR_STOMP_FRAME, frame);
        return builder.build();
    }

    private TitanSpringMessageAdapter() { }
}
