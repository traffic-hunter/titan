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
