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
package org.traffichunter.titan.core.transport.websocket;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.ChannelSecondaryIOEventLoop;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.concurrent.Promise;
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
    private static final String REQUEST = "GET /titan HTTP/1.1\r\n"
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

        assertThat(request.uri()).isEqualTo("/titan");
        assertThat(request.header("Sec-WebSocket-Key")).isEqualTo(KEY);
        assertThat(request.header("Sec-WebSocket-Protocol")).isEqualTo("v12.stomp");
    }

    @Test
    void parse_request_accepts_configured_path() {
        HttpRequest request = new WebSocketServerHandshaker("/stomp")
                .parseRequest(REQUEST.replace("GET /titan", "GET /stomp"));

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

        harness.read("GET /titan HTTP/1.1\r\nHost: localhost:8080\r\n");
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
    void upgrade_handler_fails_promise_and_closes_channel_when_request_is_invalid() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read(REQUEST.replace("Sec-WebSocket-Version: 13", "Sec-WebSocket-Version: 12"));

        assertThat(harness.promise.isFailed()).isTrue();
        assertThat(harness.promise.error())
                .isInstanceOf(WebSocketHandshakeException.class)
                .hasMessageContaining("Invalid WebSocket version");
        assertThat(harness.channel.isClosed()).isTrue();
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
        public void sparkExceptionCaught(Throwable error) {
        }
    }
}
