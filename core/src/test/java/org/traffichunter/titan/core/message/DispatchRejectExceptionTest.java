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

import org.junit.jupiter.api.Test;

/**
 * @author yun
 */
class DispatchRejectExceptionTest {

    @Test
    void confirmed_refusal_preserves_result_and_formats_message() {
        DispatchRejectException error = new DispatchRejectException(
                DispatchResult.FULL, "Queue {} refused message: {}", "/queue/orders", DispatchResult.FULL);

        assertThat(error.result()).isEqualTo(DispatchResult.FULL);
        assertThat(error.getMessage()).isEqualTo("Queue /queue/orders refused message: FULL");
    }

    @Test
    void message_without_result_does_not_establish_admission_refusal() {
        DispatchRejectException error = new DispatchRejectException("Queue {} failed", "/queue/orders");

        assertThat(error.result()).isNull();
        assertThat(error.getMessage()).isEqualTo("Queue /queue/orders failed");
    }

    @Test
    void accepted_result_cannot_be_used_for_refusal() {
        assertThatThrownBy(() -> new DispatchRejectException(DispatchResult.ACCEPTED, "Invalid refusal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Accepted messages cannot be classified as rejected");
    }
}
