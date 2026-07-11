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
    private static final char[] ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int RANDOM_ID_LENGTH = 16;

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String randomId16(@Nullable String prefix) {
        char[] randomId = new char[RANDOM_ID_LENGTH];
        for (int i = 0; i < randomId.length; i++) {
            randomId[i] = ALPHANUMERIC[SECURE_RANDOM.nextInt(ALPHANUMERIC.length)];
        }
        String value = new String(randomId);

        if (prefix == null) {
            return value;
        }

        if (prefix.isBlank()) {
            prefix = "titan";
        }
        return prefix + "-" + value;
    }

    public static String randomBase64Id16() {
        byte[] bytes = new byte[RANDOM_ID_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String name() {
        return Configurations.name();
    }

    public static String timestamp() {
        throw new UnsupportedOperationException();
    }

    private IdGenerator() { }
}
