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
package org.traffichunter.titan.core.codec.json;

import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Small JSON helper that uses the shared Jackson {@link JsonMapper}.
 *
 * <p>Methods return {@code null} when serialization or deserialization fails.</p>
 *
 * @author yungwang-o
 */
@Slf4j
public final class Json {

    private static final JsonMapper mapper = new JsonMapper();

    /**
     * Serializes an object to a JSON string.
     */
    public static @Nullable <T> String serialize(final T object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JacksonException e) {
            log.error("Failed serialize = {} ", e.getMessage());
            return null;
        }
    }

    /**
     * Serializes an object to UTF-8 JSON bytes.
     */
    public static <T> byte @Nullable [] serializeToBytes(final T object) {
        try {
            return mapper.writeValueAsBytes(object);
        } catch (JacksonException e) {
            log.error("Failed serializeToBytes = {} ", e.getMessage());
            return null;
        }
    }

    /**
     * Deserializes a JSON string into the requested type.
     */
    public static @Nullable <T> T deserialize(final String json, final Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JacksonException e) {
            log.error("Failed deserialize = {} ", e.getMessage());
            return null;
        }
    }

    /**
     * Deserializes JSON read from an input stream into the requested type.
     */
    public static @Nullable <T> T deserialize(final InputStream is, final Class<T> type) {
        try {
            return mapper.readValue(is, type);
        } catch (JacksonException e) {
            log.error("Failed deserialize = {} ", e.getMessage());
            return null;
        }
    }

    /**
     * Deserializes JSON bytes into the requested type.
     */
    public static @Nullable <T> T deserialize(final byte[] json, final Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JacksonException e) {
            log.error("Failed deserialize = {} ", e.getMessage());
            return null;
        }
    }

    private Json() {}
}
