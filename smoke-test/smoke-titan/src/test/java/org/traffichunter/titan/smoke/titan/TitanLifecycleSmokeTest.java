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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.traffichunter.titan.client.TitanClient;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Checks packaged-server and client lifecycle behavior at the process boundary.
 *
 * @author yun
 */
@TitanSmokeTest
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TitanLifecycleSmokeTest {

    @Test
    void packaged_server_process_terminates_on_shutdown(TitanRuntime runtime) throws Exception {
        runtime.start(TitanSmokeTransport.TCP);
        runtime.client();

        assertThat(runtime.stop()).as("packaged Titan process must terminate").isTrue();
        assertThat(runtime.isRunning()).isFalse();
    }

    @ParameterizedTest(name = "explicit disconnect, transport={0}")
    @EnumSource(TitanSmokeTransport.class)
    void explicit_disconnect_does_not_restore_the_connection_after_restart(
            TitanSmokeTransport transport,
            TitanRuntime runtime
    ) throws Exception {
        runtime.start(transport);
        TitanClient disconnected = runtime.client();
        disconnected.disconnect().get(10, TimeUnit.SECONDS);
        assertThat(disconnected.isConnected()).isFalse();

        runtime.restart();
        TitanClient fresh = runtime.client();
        String destination = "/queue/" + UUID.randomUUID();
        LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        fresh.subscribe(destination, frame ->
                messages.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
        fresh.send(destination, "fresh-connection").get(10, TimeUnit.SECONDS);

        assertThat(messages.poll(10, TimeUnit.SECONDS)).isEqualTo("fresh-connection");
        await().during(600, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(disconnected.isConnected()).isFalse());
    }

    @ParameterizedTest(name = "shutdown with queued sends, transport={0}")
    @EnumSource(TitanSmokeTransport.class)
    void shutdown_completes_pending_sends_and_rejects_new_sends(
            TitanSmokeTransport transport,
            TitanRuntime runtime
    ) throws Exception {
        runtime.start(transport);
        String destination = "/queue/" + UUID.randomUUID();
        TitanClient producer = runtime.client();
        TitanClient consumer = runtime.client();
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        consumer.subscribe(destination, frame ->
                received.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);

        List<CompletableFuture<StompFrames>> sends = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            sends.add(producer.send(destination, "pending-" + i));
        }
        producer.shutdown(5, TimeUnit.SECONDS);
        CompletableFuture.allOf(sends.stream()
                .map(future -> future.handle((frame, failure) -> null))
                .toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        assertThat(producer.isShutdown()).isTrue();

        Buffer rejected = Buffer.direct().alloc("after-shutdown");
        assertThatThrownBy(() -> producer.send(destination, rejected).get(10, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.ExecutionException.class);
        assertThat(rejected.byteBuf().refCnt()).as("rejected payload reference").isZero();

        runtime.client().send(destination, "healthy-marker").get(10, TimeUnit.SECONDS);
        await().atMost(10, TimeUnit.SECONDS).until(() -> received.contains("healthy-marker"));
    }
}
