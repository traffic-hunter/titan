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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import org.traffichunter.titan.core.codec.Headers;
import org.traffichunter.titan.core.codec.stomp.StompFrame.StompFrameException;

/**
 * @author yungwang-o
 */
@Getter
public final class StompHeaders extends Headers<StompHeaders.Elements, String, StompHeaders> {

    private static final char ESCAPE = '\\';
    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';
    private static final char COLON = ':';

    private static final String ESCAPE_ESCAPE = "\\\\";
    private static final String COLON_ESCAPE = "\\c";
    private static final String LINE_FEED_ESCAPE = "\\n";
    private static final String CARRIAGE_RETURN_ESCAPE = "\\r";

    private final String name;
    private final String version;

    public StompHeaders(final StompVersion version) {
        this(new HashMap<>(), version.getName(), version.getVersion());
    }

    public StompHeaders(final Map<Elements, String> map, final String name, final String version) {
        super(map);
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        this.name = name;
        this.version = version;
    }

    public static StompHeaders create() {
        return new StompHeaders(StompVersion.STOMP_1_2);
    }

    public static String encode(final String value, final StompCommand command) {
        if(value == null) {
            return "";
        }

        final boolean skipCommand = (command == StompCommand.CONNECT || command == StompCommand.CONNECTED);

        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case ESCAPE -> sb.append(skipCommand ? c : ESCAPE_ESCAPE);
                case LINE_FEED -> sb.append(skipCommand ? c : LINE_FEED_ESCAPE);
                case CARRIAGE_RETURN -> sb.append(skipCommand ? c : CARRIAGE_RETURN_ESCAPE);
                case COLON -> sb.append(skipCommand ? c : COLON_ESCAPE);
                default -> sb.append(c);
            }
        }

        return sb.toString();
    }

    public static String decode(final String value, final StompCommand command) {

        final boolean skipCommand = (command == StompCommand.CONNECT || command == StompCommand.CONNECTED);

        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < value.length();) {
            char c = value.charAt(i);

            if(c == ESCAPE && i + 1 < value.length()) {
                char next = value.charAt(i + 1);

                switch (next) {
                    case ESCAPE -> sb.append(ESCAPE);
                    case LINE_FEED -> sb.append(skipCommand ? ESCAPE : LINE_FEED);
                    case CARRIAGE_RETURN -> sb.append(skipCommand ? ESCAPE : CARRIAGE_RETURN);
                    case COLON -> sb.append(skipCommand ? ESCAPE : COLON);
                    default -> throw new StompFrameException("Illegal escape sequence: \\\\\" + next");
                }
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }

        return sb.toString();
    }

    @Override
    public void put(final Elements key, final String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        map.put(key, value);
    }

    @Override
    public void putIfAbsent(final Elements key, final String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        map.putIfAbsent(key, value);
    }

    @Override
    public Optional<String> get(final Elements key) {
        Objects.requireNonNull(key, "key");
        return Optional.of(map.get(key));
    }

    @Override
    public boolean containsKey(final Elements key) {
        Objects.requireNonNull(key, "key");
        return map.containsKey(key);
    }

    @Override
    public Set<Entry<Elements, String>> entrySet() {
        return map.entrySet();
    }

    @Override
    public Set<Elements> keySet() {
        return map.keySet();
    }

    @Override
    public Iterator<Entry<Elements, String>> iterator() {
        return map.entrySet().iterator();
    }

    @Override
    public StompHeaders getHeader() {
        return new StompHeaders(new HashMap<>(), "stomp", "1.2");
    }

    @Getter
    public enum Elements {
        ACCEPT_VERSION("accept-version"),
        HOST("host"),
        LOGIN("login"),
        PASSCODE("passcode"),
        HEART_BEAT("heart-beat"),
        VERSION("version"),
        SESSION("session"),
        SERVER("server"),
        DESTINATION("destination"),
        ID("id"),
        ACK("ack"),
        TRANSACTION("transaction"),
        RECEIPT("receipt"),
        MESSAGE_ID("message-id"),
        SUBSCRIPTION("subscription"),
        RECEIPT_ID("receipt-id"),
        MESSAGE("message"),
        CONTENT_LENGTH("content-length"),
        CONTENT_TYPE("content-type"),
        ;

        private final String name;

        Elements(final String name) {
            this.name = name;
        }
    }
}
