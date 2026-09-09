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

import io.netty.buffer.ByteBufAllocator;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.PooledByteBufAllocator;
import org.traffichunter.titan.core.codec.base64.Base64Codec;

/**
 * Allocates reference-counted buffers from Netty's pooled JVM heap storage.
 *
 * @author yun
 */
final class HeapBufferAllocator implements BufferAllocator {

    private static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    @Override
    public Buffer alloc() {
        return alloc(0);
    }

    @Override
    public Buffer alloc(int initialCapacity) {
        return new InternalBuffer(ALLOCATOR.heapBuffer(initialCapacity));
    }

    @Override
    public Buffer alloc(int initialCapacity, int maxCapacity) {
        return new InternalBuffer(ALLOCATOR.heapBuffer(initialCapacity, maxCapacity));
    }

    @Override
    public Buffer alloc(byte[] data) {
        return new InternalBuffer(ALLOCATOR.heapBuffer(data.length).writeBytes(data));
    }

    @Override
    public Buffer alloc(String data) {
        return alloc(data, StandardCharsets.UTF_8);
    }

    @Override
    public Buffer alloc(String data, Charset charset) {
        return alloc(data.getBytes(charset));
    }

    @Override
    public Buffer allocAfterBase64Decode(String data) {
        return alloc(Base64Codec.decode(data));
    }
}
