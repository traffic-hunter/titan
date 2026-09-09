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
package org.traffichunter.titan.core.codec.stomp.vertx;

import io.vertx.ext.stomp.Frame;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrames;

import java.util.HashMap;
import java.util.Map;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * @author yun
 */
@NullMarked
public final class VertxStompFrame implements StompFrames {

    private final Frame frame;

    private VertxStompFrame(Frame frame) {
        this.frame = frame;
    }

    public static VertxStompFrame wrap(Frame frame) {
        return new VertxStompFrame(frame);
    }

    public Frame unwrap() {
        return frame;
    }

    @Override
    public StompCommand command() {
        return switch (frame.getCommand()) {
            case CONNECT -> StompCommand.CONNECT;
            case CONNECTED -> StompCommand.CONNECTED;
            case STOMP -> StompCommand.STOMP;
            case SEND -> StompCommand.SEND;
            case SUBSCRIBE -> StompCommand.SUBSCRIBE;
            case UNSUBSCRIBE -> StompCommand.UNSUBSCRIBE;
            case ACK -> StompCommand.ACK;
            case NACK -> StompCommand.NACK;
            case BEGIN -> StompCommand.BEGIN;
            case COMMIT -> StompCommand.COMMIT;
            case ABORT -> StompCommand.ABORT;
            case DISCONNECT -> StompCommand.DISCONNECT;
            case MESSAGE -> StompCommand.MESSAGE;
            case RECEIPT -> StompCommand.RECEIPT;
            case ERROR -> StompCommand.ERROR;
            case PING -> StompCommand.PING;
            case UNKNOWN -> throw new IllegalArgumentException("Unknown Vert.x STOMP command");
        };
    }

    @Override
    public @Nullable String getHeader(Elements key) {
        return frame.getHeader(key.getName());
    }

    @Override
    public Map<Elements, String> headers() {
        return convertToHeaders(frame.getHeaders());
    }

    @Override
    public byte[] body() {
        if (frame.getBody() == null) {
            return new byte[]{};
        }
        return frame.getBodyAsByteArray();
    }

    private static Map<Elements, String> convertToHeaders(Map<String, String> headers) {
        Map<String, Elements> knownHeaders = Elements.toMap();
        Map<Elements, String> converted = new HashMap<>();

        headers.forEach((name, value) -> {
            Elements element = knownHeaders.get(name);
            if (element != null) {
                converted.put(element, value);
            }
        });

        return Map.copyOf(converted);
    }
}
