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
package org.traffichunter.titan.core.util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Typed key for a server option in {@code titan-env.yml}.
 *
 * <p>Server providers and fanout launchers declare the keys they read, and the application
 * registers them in one pool at startup. Option names that are not in the pool are rejected
 * before any server is created.</p>
 *
 * @author yun
 */
public final class OptionKey<T> {

    private static final ConcurrentMap<String, OptionKey<?>> POOL = new ConcurrentHashMap<>();

    private final String name;
    private final Class<T> type;
    private final Function<String, T> parser;

    private OptionKey(final String name, final Class<T> type, final Function<String, T> parser) {
        this.name = name;
        this.type = type;
        this.parser = parser;
    }

    public static OptionKey<Integer> intKey(final String name) {
        return new OptionKey<>(name, Integer.class, Integer::valueOf);
    }

    public static OptionKey<Long> longKey(final String name) {
        return new OptionKey<>(name, Long.class, Long::valueOf);
    }

    public static OptionKey<Boolean> booleanKey(final String name) {
        return new OptionKey<>(name, Boolean.class, OptionKey::parseBoolean);
    }

    public static OptionKey<String> stringKey(final String name) {
        return new OptionKey<>(name, String.class, Function.identity());
    }

    public static void register(final List<OptionKey<?>> keys) {
        for (OptionKey<?> key : keys) {
            OptionKey<?> registered = POOL.putIfAbsent(key.name, key);
            if (registered != null && registered.type != key.type) {
                throw new IllegalArgumentException(
                        "Option " + key.name + " is already registered as " + registered.type.getSimpleName());
            }
        }
    }

    public static Set<String> names() {
        return Set.copyOf(POOL.keySet());
    }

    public String name() {
        return name;
    }

    /**
     * Reads this option, or {@code null} when it is missing or blank.
     *
     * @throws IllegalArgumentException if the value cannot be parsed
     */
    public @Nullable T get(final Map<String, String> options) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        try {
            return parser.apply(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for " + name + ": " + trimmed, e);
        }
    }

    public T get(final Map<String, String> options, final T defaultValue) {
        T value = get(options);
        return value == null ? defaultValue : value;
    }

    private static Boolean parseBoolean(final String value) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("Expected true or false");
    }

    @Override
    public String toString() {
        return name;
    }
}
