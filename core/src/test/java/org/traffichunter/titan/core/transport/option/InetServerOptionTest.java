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
package org.traffichunter.titan.core.transport.option;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.ChannelWriteBufferOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class InetServerOptionTest {

    @Test
    void use_default_child_write_buffer_limits() {
        InetClientOption option = InetServerOption.builder()
                .build()
                .childOption();

        assertThat(option.writeBufferOption()).isEqualTo(ChannelWriteBufferOption.DEFAULT);
    }

    @Test
    void configure_child_write_buffer_limits_independently() {
        InetClientOption option = InetServerOption.builder()
                .childMaxPendingBytes(4096)
                .childHighWatermarkBytes(3072)
                .childLowWatermarkBytes(1024)
                .build()
                .childOption();

        assertThat(option.writeBufferOption()).isEqualTo(new ChannelWriteBufferOption(4096, 3072, 1024));
    }

    @Test
    void reject_invalid_child_write_buffer_limits() {
        assertThatThrownBy(() -> InetServerOption.builder()
                .childMaxPendingBytes(1024)
                .childHighWatermarkBytes(2048)
                .childLowWatermarkBytes(512)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPendingBytes");
    }
}
