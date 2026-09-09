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

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;

/**
 * Shared buffer sizing defaults.
 *
 * @author yun
 */
public final class Buffers {

    public static final int DEFAULT_INITIAL_CAPACITY = 4096;
    public static final int DEFAULT_MAX_CAPACITY = 65536;

    public static ByteBuffer nioBuffer(Buffer buffer) {
        return buffer.byteBuf().nioBuffer();
    }

    public static ByteBuffer readableByteBuffer(Buffer source) {
        ByteBuf byteBuf = source.byteBuf();
        return byteBuf.nioBuffer(byteBuf.readerIndex(), byteBuf.readableBytes());
    }

    public static ByteBuffer writableByteBuffer(Buffer destination) {
        ByteBuf byteBuf = destination.byteBuf();
        return byteBuf.nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());
    }

    public static void updateWriterIndex(Buffer destination, int read) {
        if (read > 0) {
            ByteBuf byteBuf = destination.byteBuf();
            byteBuf.writerIndex(byteBuf.writerIndex() + read);
        }
    }

    private Buffers() {}
}
