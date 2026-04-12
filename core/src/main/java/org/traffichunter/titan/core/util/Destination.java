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

    private boolean matchKey(final String key) {
        return ROUTING_KEY_PATTERN.matcher(key).matches();
    }

    @Override
    public String toString() {
        return "{ destination = " + path + " }";
    }
}
