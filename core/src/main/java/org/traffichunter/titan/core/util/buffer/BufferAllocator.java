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
