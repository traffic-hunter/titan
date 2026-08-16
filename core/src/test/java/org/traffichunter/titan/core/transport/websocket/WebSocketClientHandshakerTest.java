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

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.InMemoryNetChannel;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.TaskEventLoop;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.transport.websocket.WebSocketClientHandshaker.WebSocketUpgradeHandler;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.*;

/**
 * @author yun
 */
class WebSocketClientHandshakerTest {

    private static final String KEY = "dGhlIHNhbXBsZSBub25jZQ==";
    private static final String ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
    private static final String RESPONSE = "HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Accept: " + ACCEPT + "\r\n"
            + "Sec-WebSocket-Protocol: v12.stomp\r\n"
            + "\r\n";

    @Test
    void accept_key_matches_rfc_6455_example() {
        String accept = WebSocketClientHandshaker.acceptKey(KEY);

        assertThat(accept).isEqualTo(ACCEPT);
    }

    @Test
    void validate_response_accepts_switching_protocols_response() {
        assertThatNoException()
                .isThrownBy(() -> new WebSocketClientHandshaker("localhost", Protocol.STOMP)
                        .validateResponse(RESPONSE, KEY));
    }

    @Test
    void validate_response_rejects_invalid_accept_key() {
        String response = RESPONSE.replace(ACCEPT, "invalid");

        assertThatThrownBy(() -> new WebSocketClientHandshaker("localhost", Protocol.STOMP)
                .validateResponse(response, KEY))
                .isInstanceOf(WebSocketHandshakeException.class)
                .hasMessageContaining("Invalid Sec-WebSocket-Accept");
    }

    @Test
    void validate_response_rejects_different_subprotocol() {
        String response = RESPONSE.replace("v12.stomp", "v50.mqtt");

        assertThatThrownBy(() -> new WebSocketClientHandshaker("localhost", Protocol.STOMP)
                .validateResponse(response, KEY))
                .isInstanceOf(WebSocketHandshakeException.class)
                .hasMessageContaining("Invalid Sec-WebSocket-Protocol");
    }

    @Test
    void upgrade_handler_completes_when_response_arrives_in_one_read() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read(RESPONSE);

        assertThat(harness.promise.isSuccess()).isTrue();
        assertThat(harness.chain.reads).isEmpty();
    }

    @Test
    void upgrade_handler_completes_when_response_arrives_fragmented() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read("HTTP/1.1 101 Switching");
        assertThat(harness.promise.isDone()).isFalse();

        harness.read(" Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n");
        assertThat(harness.promise.isDone()).isFalse();

        // terminating CRLF + CRLF split in the middle
        harness.read("Sec-WebSocket-Accept: " + ACCEPT
                + "\r\nSec-WebSocket-Protocol: v12.stomp\r\n\r");
        assertThat(harness.promise.isDone()).isFalse();

        harness.read("\n");
        assertThat(harness.promise.isSuccess()).isTrue();
        assertThat(harness.chain.reads).isEmpty();
    }

    @Test
    void upgrade_handler_completes_when_response_arrives_byte_by_byte() {
        UpgradeHarness harness = new UpgradeHarness();

        byte[] response = RESPONSE.getBytes(ISO_8859_1);
        for (byte b : response) {
            assertThat(harness.promise.isDone()).isFalse();
            harness.read(new byte[]{b});
        }

        assertThat(harness.promise.isSuccess()).isTrue();
        assertThat(harness.chain.reads).isEmpty();
    }

    @Test
    void upgrade_handler_passes_through_bytes_arriving_with_and_after_response() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read(RESPONSE + "FRAME");

        assertThat(harness.promise.isSuccess()).isTrue();
        assertThat(harness.chain.reads).containsExactly("FRAME");

        harness.read("MORE");

        assertThat(harness.chain.reads).containsExactly("FRAME", "MORE");
    }

    @Test
    void upgrade_handler_rejects_invalid_accept_header() {
        UpgradeHarness harness = new UpgradeHarness();

        harness.read(RESPONSE.replace(ACCEPT, "invalid"));

        assertThat(harness.promise.isFailed()).isTrue();
        assertThat(harness.promise.error())
                .isInstanceOf(WebSocketHandshakeException.class)
                .hasMessageContaining("Invalid Sec-WebSocket-Accept");
        assertThat(harness.channel.isClosed()).isTrue();
    }

    private static final class UpgradeHarness {

        private final InMemoryNetChannel channel = new InMemoryNetChannel();
        private final Promise<NetChannel> promise = Promise.newPromise(new TaskEventLoop());
        private final CapturingChain chain = new CapturingChain();
        private final WebSocketUpgradeHandler handler =
                new WebSocketUpgradeHandler(
                        new WebSocketClientHandshaker("localhost", Protocol.STOMP),
                        KEY,
                        promise
                );

        private void read(String data) {
            read(data.getBytes(ISO_8859_1));
        }

        private void read(byte[] data) {
            handler.sparkChannelRead(channel, Buffer.heap().alloc(data), chain);
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
