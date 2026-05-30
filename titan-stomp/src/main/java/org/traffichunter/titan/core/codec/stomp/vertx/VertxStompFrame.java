/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
