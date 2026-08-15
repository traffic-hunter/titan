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
package org.traffichunter.titan.core.util.management;

/**
 * Aggregate management view of every active channel write buffer.
 *
 * <p>The view intentionally avoids one MBean per network connection. This keeps
 * management overhead stable while still exposing outbound pressure across the
 * process.</p>
 *
 * @author yun
 */
public interface ChannelWriteBufferMbean {

    /**
     * Returns the number of open channel write buffers attached to this event-loop group.
     *
     * <p>One active buffer corresponds to one registered network channel. The value decreases
     * when the channel closes and its write buffer releases any remaining outbound data.</p>
     */
    int getActiveBuffers();

    /**
     * Returns bytes accepted for outbound delivery but not yet written to socket buffers.
     *
     * <p>The unit is bytes. The value decreases as partial or complete socket writes make
     * progress; it does not represent application payload size after the write has completed.</p>
     */
    long getPendingBytes();

    /**
     * Returns the number of buffers currently above their configured high watermark.
     *
     * <p>A buffer enters this state after pending bytes exceed the high watermark and leaves it
     * only after pending bytes fall below the low watermark. This is Titan write-buffer pressure,
     * not the operating system selector's socket-writability state.</p>
     */
    int getNonWritableBuffers();
}
