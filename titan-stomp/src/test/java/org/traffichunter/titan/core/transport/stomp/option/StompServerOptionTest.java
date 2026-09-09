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
package org.traffichunter.titan.core.transport.stomp.option;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class StompServerOptionTest {

    @Test
    void use_one_megabyte_as_default_max_frame_length() {
        StompServerOption option = StompServerOption.builder().build();

        assertThat(option.maxFrameLength()).isEqualTo(1024 * 1024);
    }

    @Test
    void configure_max_frame_length() {
        StompServerOption option = StompServerOption.builder()
                .maxFrameLength(4096)
                .build();

        assertThat(option.maxFrameLength()).isEqualTo(4096);
    }

    @Test
    void reject_non_positive_max_frame_length() {
        assertThatThrownBy(() -> StompServerOption.builder().maxFrameLength(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxFrameLength");
    }
}
