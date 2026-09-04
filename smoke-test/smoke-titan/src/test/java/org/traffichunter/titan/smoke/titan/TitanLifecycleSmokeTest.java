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
