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
package org.traffichunter.titan.core.util.buffer;

import java.nio.charset.Charset;

/**
 * Allocates owned Titan buffers using a fixed heap or direct-memory policy.
 *
 * <p>Every allocation returns a reference-counted buffer owned by the caller. Both heap and
 * direct implementations use pooled Netty storage, so every returned buffer must be released or
 * transferred to another owner.</p>
 *
 * <p>Use heap buffers for short-lived codec and protocol work, and direct buffers for socket
 * reads and TLS processing. Long-lived
 * message and queue data should use byte arrays instead of this API.</p>
 *
 * @author yun
 */
public interface BufferAllocator {

    Buffer alloc();

    Buffer alloc(int initialCapacity);

    Buffer alloc(int initialCapacity, int maxCapacity);

    /**
     * Allocates a buffer and copies the supplied bytes into its storage.
     */
    Buffer alloc(byte[] data);

    /**
     * Allocates a buffer containing the UTF-8 representation of the supplied string.
     */
    Buffer alloc(String data);

    Buffer alloc(String data, Charset charset);

    /**
     * Decodes Base64 text and copies the decoded bytes into a new buffer.
     */
    Buffer allocAfterBase64Decode(String data);

    /**
     * Allocates an empty buffer using this allocator's memory policy.
     */
    default Buffer empty() {
        return alloc(0);
    }
}
