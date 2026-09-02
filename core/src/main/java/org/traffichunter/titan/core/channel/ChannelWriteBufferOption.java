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
package org.traffichunter.titan.core.channel;

/**
 * Outbound channel buffering limits.
 *
 * <p>The high and low watermarks provide hysteresis for channel writability. The hard maximum
 * rejects additional outbound data before pending bytes can grow without bound.</p>
 *
 * @author yun
 */
public record ChannelWriteBufferOption(
        int maxPendingBytes,
        int highWatermarkBytes,
        int lowWatermarkBytes
) {

    public static final int DEFAULT_HIGH_WATERMARK_BYTES = 64 * 1024;
    public static final int DEFAULT_LOW_WATERMARK_BYTES = 32 * 1024;
    public static final int DEFAULT_MAX_PENDING_BYTES = DEFAULT_HIGH_WATERMARK_BYTES * 2;

    public static final ChannelWriteBufferOption DEFAULT = new ChannelWriteBufferOption(
            DEFAULT_MAX_PENDING_BYTES,
            DEFAULT_HIGH_WATERMARK_BYTES,
            DEFAULT_LOW_WATERMARK_BYTES
    );

    public ChannelWriteBufferOption {
        if (lowWatermarkBytes <= 0) {
            throw new IllegalArgumentException("lowWatermarkBytes must be greater than zero");
        }
        if (highWatermarkBytes <= lowWatermarkBytes) {
            throw new IllegalArgumentException("highWatermarkBytes must be greater than lowWatermarkBytes");
        }
        if (maxPendingBytes < highWatermarkBytes) {
            throw new IllegalArgumentException(
                    "maxPendingBytes must be greater than or equal to highWatermarkBytes"
            );
        }
    }
}
