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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.bootstrap.Configurations;

/**
 * @author yungwang-o
 */
public final class IdGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RANDOM_ID_BYTES = 16;

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String randomId(@Nullable String prefix) {
        if (prefix == null) {
            prefix = "titan";
        }

        byte[] bytes = new byte[RANDOM_ID_BYTES];
        SECURE_RANDOM.nextBytes(bytes);

        return prefix + "-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String name() {
        return Configurations.name();
    }

    public static String timestamp() {
        throw new UnsupportedOperationException();
    }

    private IdGenerator() { }
}
