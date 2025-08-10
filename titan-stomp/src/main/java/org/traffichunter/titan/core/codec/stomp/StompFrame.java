/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.codec.stomp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.Pair;

/**
 * @author yungwang-o
 */
@Getter
@Slf4j
public class StompFrame {

    public static final StompFrame ERR_STOMP_FRAME =
            StompFrame.create(new StompHeaders(new HashMap<>(), "stomp", "1.2"), StompCommand.ERROR);

    private final StompHeaders headers;

    private final StompCommand command;

    private final byte[] body;

    private StompFrame(final StompHeaders headers, final StompCommand command) {
        this(headers, command, new byte[] {});
    }

    private StompFrame(final StompHeaders headers, final StompCommand command, final byte[] body) {
        Objects.requireNonNull(headers);
        Objects.requireNonNull(command);
        Objects.requireNonNull(body);
        this.headers = headers;
        this.command = command;
        this.body = body;
    }

    public static StompFrame create(final StompHeaders headers, final StompCommand command) {
        return new StompFrame(headers, command);
    }

    public static StompFrame create(final StompHeaders headers,
                                    final StompCommand command,
                                    final byte[] body) {

        return new StompFrame(headers, command, body);
    }

    public void addHeader(final Elements key, final String value) {
        headers.putIfAbsent(key, value);
    }

    public String getHeader(final Elements key) {
        return headers.get(key).orElseThrow(() -> new StompFrameException("Missing header " + key));
    }

    public ByteBuffer toBuffer() {
        return ByteBuffer.wrap(toString().getBytes(StandardCharsets.UTF_8));
    }

    public String toStringForLogging() {
        return toString(true);
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(final boolean isLogging) {
        final StringBuilder sb = new StringBuilder();
        sb.append(command.name());

        // CRLF
        sb.append(StompDelimiter.CR.getCharacter()).append(StompDelimiter.LF.getCharacter());

        Set<Entry<Elements, String>> entries = headers.entrySet();
        for(Entry<Elements, String> entry : entries) {
            sb.append(entry.getKey().getName().toLowerCase());
            sb.append(StompDelimiter.COLON.getCharacter());

            if(entry.getKey() == Elements.PASSCODE) {
                sb.append("*****");
            } else {
                sb.append(entry.getValue());
            }
            sb.append(StompDelimiter.CR.getCharacter()).append(StompDelimiter.LF.getCharacter());
        }
        sb.append(StompDelimiter.CR.getCharacter()).append(StompDelimiter.LF.getCharacter());

        if(body != null) {
            if(isLogging && body.length >= 100) {
                String loggingStr = new String(body, StandardCharsets.UTF_8);

                String pre = loggingStr.substring(0, 30);
                String post = loggingStr.substring(body.length - 30);
                sb.append(pre).append(".............").append(post);
            } else {
                sb.append(new String(body, StandardCharsets.UTF_8));
            }
        }

        sb.append(StompDelimiter.NUL.getCharacter());
        return sb.toString();
    }

    public static class HeartBeat {
        final long x;
        final long y;

        private HeartBeat(final long x, final long y) {
            this.x = x;
            this.y = y;
        }

        public static final Pair<Long, Long> DEFAULT_HEARTBEAT = new Pair<>(1_000L, 1_000L);

        public static HeartBeat create(final long x, final long y) {
            return new HeartBeat(x, y);
        }

        public static HeartBeat create(final Pair<Long, Long> heartbeat) {
            return new HeartBeat(heartbeat.first(), heartbeat.second());
        }

        public static HeartBeat doParse(final String header) {
            if (header == null) {
                return new HeartBeat(0, 0);
            }

            String[] token = header.split(StompDelimiter.COMMA.getString());
            return new HeartBeat(Long.parseLong(token[0]), Long.parseLong(token[1]));
        }

        @Override
        public String toString() {
            return "HeartBeat{" + "x=" + x + ", y=" + y + '}';
        }
    }

    public static String errorFrame(final String message, final Object... obj) {
        return MessageFormatter.basicArrayFormat(message, obj);
    }

    public static class StompFrameException extends StompException {

        public StompFrameException() {
        }

        public StompFrameException(final String message) {
            super(message);
        }

        public StompFrameException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public StompFrameException(final Throwable cause) {
            super(cause);
        }

        public StompFrameException(final String message, final Throwable cause, final boolean enableSuppression,
                                   final boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
