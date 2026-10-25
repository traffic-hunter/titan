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
package org.traffichunter.titan.core.transport.websocket;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.ChannelSecondaryIOEventLoop;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.net.HttpRequest;
import org.traffichunter.titan.core.transport.websocket.WebSocketServerHandshaker.WebSocketUpgradeHandler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class WebSocketServerHandshakerTest {

    private static final ChannelSecondaryIOEventLoop EVENT_LOOP =
            new ChannelSecondaryIOEventLoop("websocket-server-handshaker-test");
    private static final String KEY = "dGhlIHNhbXBsZSBub25jZQ==";
    private static final String ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
    private static final String REQUEST = "GET / HTTP/1.1\r\n"
            + "Host: localhost:8080\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: keep-alive, Upgrade\r\n"
            + "Sec-WebSocket-Key: " + KEY + "\r\n"
            + "Sec-WebSocket-Protocol: v12.stomp\r\n"
            + "Sec-WebSocket-Version: 13\r\n"
            + "\r\n";

    @BeforeAll
    static void startEventLoop() {
        EVENT_LOOP.start();
    }

    @AfterAll
    static void stopEventLoop() {
        EVENT_LOOP.gracefullyShutdown(1, TimeUnit.SECONDS);
    }

    @Test
    void parse_request_extracts_websocket_upgrade_headers() {
        HttpRequest request = new WebSocketServerHandshaker().parseRequest(REQUEST);

        assertThat(request.uri()).isEqualTo("/");
        assertThat(request.header("Sec-WebSocket-Key")).isEqualTo(KEY);
        assertThat(request.header("Sec-WebSocket-Protocol")).isEqualTo("v12.stomp");
    }

    @Test
    void parse_request_accepts_configured_path() {
        HttpRequest request = new WebSocketServerHandshaker("stomp")
                .parseRequest(REQUEST.replace("GET / ", "GET /stomp "));

        assertThat(request.uri()).isEqualTo("/stomp");
    }

    @Test
    void create_response_returns_switching_protocols_response() {
        WebSocketServerHandshaker handshaker = new WebSocketServerHandshaker();
        HttpRequest request = handshaker.parseRequest(REQUEST);

        String response = handshaker.createResponse(request);

        assertThat(response).isEqualTo("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + ACCEPT + "\r\n"
                + "Sec-WebSocket-Protocol: v12.stomp\r\n"
                + "\r\n");
    }

    @Test
    void create_response_selects_stomp_from_offered_subprotocols() {
        WebSocketServerHandshaker handshaker = new WebSocketServerHandshaker();
        HttpRequest request = handshaker.parseRequest(
                REQUEST.replace("Sec-WebSocket-Protocol: v12.stomp", "Sec-WebSocket-Protocol: foo, v12.stomp"));

        assertThat(handshaker.createResponse(request)).contains("Sec-WebSocket-Protocol: v12.stomp\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sec-WebSocket-Protocol: foo\r\n", "Sec-WebSocket-Protocol: V12.STOMP\r\n", ""})
    void create_response_omits_subprotocol_when_stomp_is_not_offered(String protocolHeader) {
        WebSocketServerHandshaker handshaker = new WebSocketServerHandshaker();
        HttpRequest request = handshaker.parseRequest(
                REQUEST.replace("Sec-WebSocket-Protocol: v12.stomp\r\n", protocolHeader));

        String response = handshaker.createResponse(request);

        assertThat(response).startsWith("HTTP/1.1 101 Switching Protocols");
        assertThat(response).doesNotContain("Sec-WebSocket-Protocol");
    }

    @Test
    void parse_request_rejects_invalid_upgrade_request() {
        String request = REQUEST.replace("Upgrade: websocket", "Upgrade: h2c");

        assertThatThrownBy(() -> new WebSocketServerHandshaker().parseRequest(request))
                .isInstanceOf(WebSocketHandshakeException.class)
                .hasMessageContaining("Invalid WebSocket Upgrade header");
    }

    @Test
    void parse_request_rejects_unexpected_path() {
        WebSocketServerHandshaker handshaker = new WebSocketServerHandshaker("/stomp");

        assertThatThrownBy(() -> handshaker.parseRequest(REQUEST))
                .isInstanceOf(WebSocketHandshakeException.class)
                .hasMessageContaining("Unexpected WebSocket path");
    }

    @Test
    void upgrade_handler_completes_when_request_arrives_in_one_read() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read(REQUEST);

        String response = harness.written();
        assertThat(harness.promise.isSuccess()).isTrue();
        assertThat(response).contains("HTTP/1.1 101 Switching Protocols");
        assertThat(response).contains("Sec-WebSocket-Accept: " + ACCEPT);
        assertThat(harness.chain.reads).isEmpty();
    }

    @Test
    void upgrade_handler_completes_when_request_arrives_fragmented() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read("GET / HTTP/1.1\r\nHost: localhost:8080\r\n");
        assertThat(harness.promise.isDone()).isFalse();

        harness.read("Upgrade: websocket\r\nConnection: keep-alive, Upgrade\r\n");
        assertThat(harness.promise.isDone()).isFalse();

        harness.read("Sec-WebSocket-Key: " + KEY + "\r\n"
                + "Sec-WebSocket-Protocol: v12.stomp\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r");
        assertThat(harness.promise.isDone()).isFalse();

        harness.read("\n");

        assertThat(harness.promise.isSuccess()).isTrue();
        assertThat(harness.written()).contains("HTTP/1.1 101 Switching Protocols");
    }

    @Test
    void upgrade_handler_passes_through_bytes_arriving_with_and_after_request() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read(REQUEST + "FRAME");

        assertThat(harness.promise.isSuccess()).isTrue();
        assertThat(harness.chain.reads).containsExactly("FRAME");

        harness.read("MORE");

        assertThat(harness.chain.reads).containsExactly("FRAME", "MORE");
    }

    @Test
    void upgrade_handler_fails_promise_when_request_is_invalid() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read(REQUEST.replace("Sec-WebSocket-Version: 13", "Sec-WebSocket-Version: 12"));

        assertThat(harness.promise.isFailed()).isTrue();
        assertThat(harness.promise.error())
                .isInstanceOf(WebSocketHandshakeException.class)
                .hasMessageContaining("Invalid WebSocket version");
        assertThat(harness.channel.writtenCount()).isZero();
    }

    @Test
    void upgrade_handler_ignores_input_after_failure() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read(REQUEST.replace("Upgrade: websocket", "Upgrade: h2c") + REQUEST);
        harness.read(REQUEST);

        assertThat(harness.promise.isFailed()).isTrue();
        assertThat(harness.channel.writtenCount()).isZero();
        assertThat(harness.chain.reads).isEmpty();
    }

    @Test
    void upgrade_handler_rejects_head_longer_than_limit() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read("GET / HTTP/1.1\r\nX-Padding: " + "a".repeat(8192) + "\r\n");

        assertThat(harness.promise.isFailed()).isTrue();
        assertThat(status(harness.promise.error())).isEqualTo(431);
    }

    @Test
    void parse_request_maps_failures_to_http_status() {
        WebSocketServerHandshaker handshaker = new WebSocketServerHandshaker("/stomp");
        String request = REQUEST.replace("GET / ", "GET /stomp ");

        assertThat(statusOf(handshaker, request.replace("GET /stomp ", "POST /stomp "))).isEqualTo(400);
        assertThat(statusOf(handshaker, request.replace("Host: localhost:8080\r\n", ""))).isEqualTo(400);
        assertThat(statusOf(handshaker, request.replace("Connection: keep-alive, Upgrade", "Connection: keep-alive")))
                .isEqualTo(400);
        assertThat(statusOf(handshaker, request.replace(KEY, "c2hvcnQ="))).isEqualTo(400);
        assertThat(statusOf(handshaker, request.replace("Sec-WebSocket-Version: 13", "Sec-WebSocket-Version: 8")))
                .isEqualTo(426);
        assertThat(statusOf(handshaker, request.replace("Sec-WebSocket-Version: 13\r\n", ""))).isEqualTo(426);
        assertThat(statusOf(handshaker, REQUEST)).isEqualTo(404);
    }

    @Test
    void error_response_advertises_supported_version_for_upgrade_required() {
        String response = new WebSocketServerHandshaker()
                .errorResponse(new WebSocketHandshakeException(426, "Invalid WebSocket version"));

        assertThat(response).isEqualTo("HTTP/1.1 426 Upgrade Required\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: close\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n");
    }

    @Test
    void error_response_uses_bad_request_for_unexpected_failures() {
        String response = new WebSocketServerHandshaker().errorResponse(new IllegalStateException("boom"));

        assertThat(response).isEqualTo("HTTP/1.1 400 Bad Request\r\n"
                + "Connection: close\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n");
    }

    private static int statusOf(WebSocketServerHandshaker handshaker, String request) {
        try {
            handshaker.parseRequest(request);
        } catch (WebSocketHandshakeException e) {
            return e.status();
        }
        throw new AssertionError("Expected the request to be rejected");
    }

    private static int status(Throwable error) {
        assertThat(error).isInstanceOf(WebSocketHandshakeException.class);
        return ((WebSocketHandshakeException) error).status();
    }

    private static final class UpgradeHarness {

        private final InMemoryNetChannel channel = new InMemoryNetChannel();
        private final Promise<NetChannel> promise = Promise.newPromise(EVENT_LOOP);
        private final CapturingChain chain = new CapturingChain();
        private final WebSocketUpgradeHandler handler =
                new WebSocketUpgradeHandler(new WebSocketServerHandshaker(), promise);

        private UpgradeHarness() {
            channel.register(EVENT_LOOP, EVENT_LOOP.newPromise(channel));
        }

        private void read(String data) {
            try {
                EVENT_LOOP.submit(() ->
                        handler.sparkChannelRead(
                                channel,
                                Buffer.heap().alloc(data.getBytes(ISO_8859_1)),
                                chain
                        )
                ).get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError("Failed to process WebSocket upgrade input", e);
            }
        }

        private String written() {
            Buffer written = channel.pollWritten();
            assertThat(written).isNotNull();
            try {
                return written.toString(ISO_8859_1);
            } finally {
                written.release();
            }
        }
    }

    private static final class CapturingChain implements ChannelInBoundHandlerChain {

        private final List<String> reads = new ArrayList<>();

        @Override
        public void sparkChannelConnecting(NetChannel channel) {
        }

        @Override
        public void sparkChannelAfterConnected(NetChannel channel) {
        }

        @Override
        public void sparkChannelRead(NetChannel channel, Buffer buffer) {
            try {
                reads.add(buffer.toString(ISO_8859_1));
            } finally {
                buffer.release();
            }
        }

        @Override
        public void sparkChannelWritabilityChanged(NetChannel channel, boolean writable) {
        }

        @Override
        public void sparkExceptionCaught(Throwable error) {
        }
    }
}
