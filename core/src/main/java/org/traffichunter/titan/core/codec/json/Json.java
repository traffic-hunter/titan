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
package org.traffichunter.titan.core.codec.json;

import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public final class Json {

    private static final Logger log = LoggerFactory.getLogger(Json.class);

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
