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
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.client.TitanClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that malformed input and stalled readers remain isolated to their connections.
 *
 * @author yun
 */
@TitanSmokeTest
@Timeout(value = 90, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TitanConnectionIsolationSmokeTest {

    @Test
    void fragmented_and_coalesced_sends_reach_the_subscriber_once(TitanRuntime runtime) throws Exception {
        runtime.start(TitanSmokeTransport.TCP);
        try (SmokeStompPeer producer = new SmokeStompPeer(runtime.port())) {
            String destination = "/queue/" + UUID.randomUUID();
            LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
            runtime.client().subscribe(destination, frame ->
                    messages.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);

            producer.send("SEND\ndestination:" + destination + "\n\nfrag");
            assertThat(messages.poll(200, TimeUnit.MILLISECONDS)).as("incomplete frame").isNull();
            producer.send("mented\0");
            assertThat(messages.poll(10, TimeUnit.SECONDS)).isEqualTo("fragmented");

            producer.send("SEND\ndestination:" + destination + "\n\none\0"
                    + "SEND\ndestination:" + destination + "\n\ntwo\0");
            String first = messages.poll(10, TimeUnit.SECONDS);
            String second = messages.poll(10, TimeUnit.SECONDS);
            assertThat(new String[]{first, second}).containsExactlyInAnyOrder("one", "two");
            assertThat(messages.poll(200, TimeUnit.MILLISECONDS)).as("duplicate frame").isNull();
        }
    }

    @Test
    void oversized_unterminated_frame_gets_error_and_closes_only_its_connection(TitanRuntime runtime)
            throws Exception {
        runtime.start(TitanSmokeTransport.TCP, 512);
        try (SmokeStompPeer invalid = new SmokeStompPeer(runtime.port())) {
            String destination = "/queue/" + UUID.randomUUID();
            TitanClient healthy = runtime.client();
            LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
            healthy.subscribe(destination, frame ->
                    received.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);

            invalid.send("SEND\ndestination:" + destination + "\n\n" + "x".repeat(1024));
            assertThat(invalid.readFrame()).startsWith("ERROR\n");
            assertThat(invalid.awaitEof()).as("oversized sender must be closed").isTrue();

            healthy.send(destination, "still-alive").get(10, TimeUnit.SECONDS);
            assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("still-alive");
            assertThat(healthy.isConnected()).isTrue();
        }
    }

    @Test
    void abandoned_partial_frames_do_not_stop_the_shared_event_loop(TitanRuntime runtime) throws Exception {
        runtime.start(TitanSmokeTransport.TCP);
        String destination = "/queue/" + UUID.randomUUID();
        TitanClient healthy = runtime.client();
        LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        healthy.subscribe(destination, frame ->
                messages.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);

        for (int attempt = 0; attempt < 5; attempt++) {
            try (SmokeStompPeer abandoned = new SmokeStompPeer(runtime.port())) {
                abandoned.send("SEND\ndestination:" + destination + "\n\nunfinished");
            }
            healthy.send(destination, "marker-" + attempt).get(10, TimeUnit.SECONDS);
            assertThat(messages.poll(10, TimeUnit.SECONDS)).isEqualTo("marker-" + attempt);
        }
    }

    @RepeatedTest(3)
    void non_reading_subscriber_does_not_prevent_healthy_delivery(TitanRuntime runtime) throws Exception {
        runtime.start(TitanSmokeTransport.TCP);
        try (SmokeStompPeer slow = new SmokeStompPeer(runtime.port())) {
            String destination = "/topic/" + UUID.randomUUID();
            slow.send("SUBSCRIBE\nid:slow\ndestination:" + destination + "\nack:auto\nreceipt:ready\n\n\0");
            assertThat(slow.readFrame()).startsWith("RECEIPT\n").contains("receipt-id:ready");

            TitanClient producer = runtime.client();
            TitanClient healthy = runtime.client();
            LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
            healthy.subscribe(destination, frame ->
                    messages.add(new String(frame.body(), StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);

            for (int i = 0; i < 512; i++) {
                String payload = i + ":" + "x".repeat(8 * 1024);
                producer.send(destination, payload).get(10, TimeUnit.SECONDS);
                assertThat(messages.poll(10, TimeUnit.SECONDS)).as("healthy delivery %s", i).isEqualTo(payload);
            }
            assertThat(healthy.isConnected()).isTrue();
        }
    }
}
