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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author yungwang-o
 */
public class SerializeUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> String serialize(final T object) throws JsonProcessingException {
        return mapper.writeValueAsString(object);
    }

    public static <T> byte[] serializeToBytes(final T object) throws JsonProcessingException {
        return mapper.writeValueAsBytes(object);
    }

    public static <T> T deserialize(final String json, final Class<T> type) throws JsonProcessingException {
        return mapper.readValue(json, type);
    }

    public static <T> T deserialize(final InputStream is, final Class<T> type) throws IOException {
        return mapper.readValue(is, type);
    }

    public static <T> T deserialize(final byte[] json, final Class<T> type) throws IOException {
        return mapper.readValue(json, type);
    }
}
