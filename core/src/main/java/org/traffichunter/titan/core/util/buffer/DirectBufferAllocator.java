/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.traffichunter.titan.core.util.buffer;

import io.netty.buffer.ByteBufAllocator;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.PooledByteBufAllocator;
import org.traffichunter.titan.core.codec.base64.Base64Codec;

/**
 * Allocates reference-counted buffers from Netty's pooled native memory.
 *
 * @author yun
 */
final class DirectBufferAllocator implements BufferAllocator {

    private static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    @Override
    public Buffer alloc() {
        return alloc(0);
    }

    @Override
    public Buffer alloc(int initialCapacity) {
        return new InternalBuffer(ALLOCATOR.directBuffer(initialCapacity));
    }

    @Override
    public Buffer alloc(int initialCapacity, int maxCapacity) {
        return new InternalBuffer(ALLOCATOR.directBuffer(initialCapacity, maxCapacity));
    }

    @Override
    public Buffer alloc(byte[] data) {
        return new InternalBuffer(ALLOCATOR.directBuffer(data.length).writeBytes(data));
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
