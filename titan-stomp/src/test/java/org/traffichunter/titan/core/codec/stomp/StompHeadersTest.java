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
package org.traffichunter.titan.core.codec.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.codec.stomp.StompFrame.StompFrameException;

/**
 * @author yun
 */
class StompHeadersTest {

    @Test
    void decoding_an_encoded_value_gives_back_the_value() {
        String value = "run:1\\part\ntwo\rthree";

        String encoded = StompHeaders.encode(value, StompCommand.SEND);

        assertThat(encoded).isEqualTo("run\\c1\\\\part\\ntwo\\rthree");
        assertThat(StompHeaders.decode(encoded, StompCommand.SEND)).isEqualTo(value);
    }

    @Test
    void a_value_with_nothing_to_escape_is_untouched_in_both_directions() {
        String value = "/queue/orders";

        assertThat(StompHeaders.encode(value, StompCommand.SEND)).isEqualTo(value);
        assertThat(StompHeaders.decode(value, StompCommand.SEND)).isEqualTo(value);
    }

    @Test
    void connect_and_connected_values_pass_through_unescaped() {
        String value = "host:1234\\5";

        for (StompCommand command : new StompCommand[]{StompCommand.CONNECT, StompCommand.CONNECTED}) {
            // These two frames are exempt from escaping, so decoding must not read a backslash
            // in them as the start of an escape sequence.
            assertThat(StompHeaders.encode(value, command)).isEqualTo(value);
            assertThat(StompHeaders.decode(value, command)).isEqualTo(value);
        }
    }

    @Test
    void an_escape_sequence_stomp_does_not_define_is_a_frame_error() {
        assertThatThrownBy(() -> StompHeaders.decode("a\\tb", StompCommand.SEND))
                .isInstanceOf(StompFrameException.class)
                .hasMessageContaining("Illegal escape sequence");
    }

    @Test
    void a_value_that_ends_mid_escape_is_a_frame_error() {
        assertThatThrownBy(() -> StompHeaders.decode("value\\", StompCommand.SEND))
                .isInstanceOf(StompFrameException.class)
                .hasMessageContaining("Illegal trailing escape");
    }
}
