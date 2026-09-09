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
package org.traffichunter.titan.core.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * @author yun
 */
class MessageTest {

    @Test
    void copy_payload_before_storing_message() {
        byte[] payload = {1, 2, 3};

        Message message = Message.builder()
                .destination(Destination.create("/queue/test"))
                .createdAt(Instant.now())
                .producerId("producer")
                .body(payload)
                .build();

        payload[0] = 9;

        assertThat(message.getBody()).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void message_without_group_lands_in_default_group() {
        Message message = Message.builder()
                .destination(Destination.create("/queue/test"))
                .createdAt(Instant.now())
                .producerId("producer")
                .body(new byte[]{1})
                .build();

        assertThat(message.getGroup()).isEqualTo(DestinationGroups.DEFAULT);
    }

    @Test
    void builder_keeps_explicit_group() {
        Message message = Message.builder()
                .group("market")
                .destination(Destination.create("/queue/test"))
                .createdAt(Instant.now())
                .producerId("producer")
                .body(new byte[]{1})
                .build();

        assertThat(message.getGroup()).isEqualTo("market");
    }

    @Test
    void build_rejects_invalid_group() {
        Message.MessageBuilder builder = Message.builder()
                .group("bad/name")
                .destination(Destination.create("/queue/test"))
                .createdAt(Instant.now())
                .producerId("producer")
                .body(new byte[]{1});

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
    }
}
