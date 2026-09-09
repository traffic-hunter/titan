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

import java.util.*;
import java.util.Map.Entry;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.frame.Headers;
import org.traffichunter.titan.core.codec.stomp.StompFrame.StompFrameException;

/**
 * @author yungwang-o
 */
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

    public StompHeaders(final Map<Elements, String> headers) {
        this(headers, StompVersion.STOMP_1_2);
    }

    public StompHeaders(final Map<Elements, String> headers, final StompVersion version) {
        this(new HashMap<>(headers), version.getName(), version.getVersion());
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

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public static String encode(@Nullable final String value, final StompCommand command) {
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

    public Map<StompHeaders.Elements, String> toMap() {
        return Map.copyOf(map);
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
    public String getOrDefault(Elements key, String defaultValue) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(defaultValue, "defaultValue");
        return map.getOrDefault(key, defaultValue);
    }

    @Override
    public @Nullable String get(final Elements key) {
        Objects.requireNonNull(key, "key");
        return map.get(key);
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

        public String getName() {
            return name;
        }

        public static Elements convertToElements(final String value) {
            return Optional.ofNullable(toMap().get(value))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown element: " + value));
        }

        public static Map<String, Elements> toMap() {
            Map<String, Elements> map = new HashMap<>();
            Arrays.stream(values()).forEach(elements -> map.put(elements.getName(), elements));

            return Collections.unmodifiableMap(map);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("StompHeaders{ ");
        for(Entry<Elements, String> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(" : ").append(entry.getValue()).append(", ");
        }
        sb.append("}");
        return sb.toString();
    }
}
