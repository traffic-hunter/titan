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

import java.util.regex.Pattern;

/**
 * @author yungwang-o
 */
public record Destination(String path) {

    private static final Pattern ROUTING_KEY_PATTERN =
            Pattern.compile("^/([a-zA-Z0-9_-]+)(/([a-zA-Z0-9_-]+))*(/\\*)?$");

    public Destination {
        if(!matchKey(path)) {
            throw new IllegalArgumentException("Invalid routing key: " + path);
        }
    }

    public static Destination create(final String routingKey) {
        return new Destination(routingKey);
    }

    public boolean startsWith(final String prefix) {
       return path.startsWith(prefix);
    }

    public boolean startsWith(final Destination prefix) {
        return path.startsWith(prefix.path);
    }

    public boolean contains(final String routingKey) {
        return path.contains(routingKey);
    }

    public boolean contains(final Destination prefix) {
        return path.contains(prefix.path);
    }

    public static boolean matchKey(final String key) {
        return ROUTING_KEY_PATTERN.matcher(key).matches();
    }

    @Override
    public String toString() {
        return "{ destination = " + path + " }";
    }
}
