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
