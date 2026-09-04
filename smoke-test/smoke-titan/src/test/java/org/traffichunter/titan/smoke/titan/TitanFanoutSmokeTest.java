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
