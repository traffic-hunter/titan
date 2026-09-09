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
package org.traffichunter.titan.smoke.titan;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.client.TitanClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements.ID;

/**
 * Verifies that the packaged dispatch runtime fans one message out to every subscriber.
 *
 * @author yun
 */
@TitanSmokeTest
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TitanFanoutSmokeTest {

    @Test
    void producer_send_is_received_by_subscribed_consumers(TitanRuntime runtime) throws Exception {
        runtime.start(TitanSmokeTransport.TCP);
        String destination = "/topic/smoke-titan/" + UUID.randomUUID();
        CountDownLatch received = new CountDownLatch(2);
        AtomicReference<String> firstPayload = new AtomicReference<>();
        AtomicReference<String> secondPayload = new AtomicReference<>();

        TitanClient firstConsumer = runtime.client();
        TitanClient secondConsumer = runtime.client();
        firstConsumer.subscribe(destination, Map.of(ID, "smoke-fanout-first"), frame -> {
            firstPayload.set(new String(frame.body(), StandardCharsets.UTF_8));
            received.countDown();
        }).get(10, TimeUnit.SECONDS);
        secondConsumer.subscribe(destination, Map.of(ID, "smoke-fanout-second"), frame -> {
            secondPayload.set(new String(frame.body(), StandardCharsets.UTF_8));
            received.countDown();
        }).get(10, TimeUnit.SECONDS);

        runtime.client().send(destination, "smoke-message").get(10, TimeUnit.SECONDS);

        assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(firstPayload.get()).isEqualTo("smoke-message");
        assertThat(secondPayload.get()).isEqualTo("smoke-message");
    }
}
