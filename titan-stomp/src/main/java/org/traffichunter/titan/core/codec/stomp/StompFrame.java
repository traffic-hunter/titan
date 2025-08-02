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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

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

    public String toStringForLogging() {
        return toString(true);
    }

    public String toString() {
        return toString(false);
    }

    @CanIgnoreReturnValue
    public StompFrame addHeader(final Elements key, final String value) {
        headers.putIfAbsent(key, value);
        return this;
    }

    private String toString(final boolean isLogging) {
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
        final int x;
        final int y;

        public HeartBeat(final int x, final int y) {
            this.x = x;
            this.y = y;
        }

        public static HeartBeat doParse(final String header) {
            if (header == null) {
                return new HeartBeat(0, 0);
            }

            String[] token = header.split(StompDelimiter.COMMA.getString());
            return new HeartBeat(Integer.parseInt(token[0]), Integer.parseInt(token[1]));
        }

        @Override
        public String toString() {
            return x + "," + y;
        }

        public static long computePingPeriod(HeartBeat client, HeartBeat server) {
            if (client.x == 0 || server.y == 0) {
                return 0;
            }
            return Math.max(client.x, server.y);
        }

        public static long computePongPeriod(HeartBeat client, HeartBeat server) {
            if (client.x == 0 || server.y == 0) {
                return 0;
            }
            return Math.max(client.y, server.x);
        }
    }
}
