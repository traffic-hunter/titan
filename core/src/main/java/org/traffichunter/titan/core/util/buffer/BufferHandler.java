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
package org.traffichunter.titan.core.util.buffer;

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

/**
 * @author yungwang-o
 */
public class BufferHandler {

    public static ByteBuf copyBuffer(final ByteBuf buffer) {

        if(buffer != Unpooled.EMPTY_BUFFER && requiresCopy(buffer)) {
            try {
                if (buffer.isReadable()) {
                    ByteBuf byteBuf = AutoManagedByteBufAllocator.DEFAULT.directBuffer(buffer.readableBytes());
                    byteBuf.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
                    return byteBuf;
                } else {
                    return Unpooled.EMPTY_BUFFER;
                }
            } finally {
                buffer.release();
            }
        }

        return buffer;
    }

    private static boolean requiresCopy(final ByteBuf buffer) {
        final Class<? extends ByteBufAllocator> bufferClazz = buffer.alloc().getClass();

        return bufferClazz == AdaptiveByteBufAllocator.class ||
                bufferClazz == PooledByteBufAllocator.class ||
                buffer instanceof CompositeByteBuf;
    }

    private BufferHandler() { }
}
