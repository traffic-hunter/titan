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
import org.jspecify.annotations.Nullable;

/**
 * Naming rules for destination groups.
 *
 * <p>A group is a namespace for destinations. The same destination may exist in several
 * groups as separate queues. Traffic that names no group belongs to {@value #DEFAULT}.
 * Group names use the same character set as a destination segment and are capped at
 * 64 characters.</p>
 *
 * @author yun
 */
public final class DestinationGroups {

    /** Group used when a message or subscription names none. */
    public static final String DEFAULT = "default";

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private DestinationGroups() {
    }

    public static boolean isValid(final String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isDefault(final String name) {
        return DEFAULT.equals(name);
    }

    /**
     * Resolves a group name taken from user input.
     *
     * <p>A missing or blank name means the default group, so a client that omits the
     * header and one that sends it empty are treated the same.</p>
     *
     * @throws IllegalArgumentException when the name is present but malformed
     */
    public static String normalize(final @Nullable String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        if (!isValid(name)) {
            throw new IllegalArgumentException("Invalid group name: " + name);
        }
        return name;
    }
}
