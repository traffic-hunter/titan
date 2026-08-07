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
package org.traffichunter.titan.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class StompEndpointTest {

    @Test
    void parse_tcp_endpoint() {
        StompEndpoint endpoint = StompEndpoint.parse("tcp://localhost:61613");

        assertThat(endpoint.scheme()).isEqualTo(StompEndpoint.Scheme.TCP);
        assertThat(endpoint.host()).isEqualTo("localhost");
        assertThat(endpoint.port()).isEqualTo(61613);
        assertThat(endpoint.path()).isEmpty();
        assertThat(endpoint.isWebSocket()).isFalse();
    }

    @Test
    void parse_websocket_endpoint() {
        StompEndpoint endpoint = StompEndpoint.parse("ws://localhost:8080/stomp");

        assertThat(endpoint.scheme()).isEqualTo(StompEndpoint.Scheme.WS);
        assertThat(endpoint.port()).isEqualTo(8080);
        assertThat(endpoint.path()).isEqualTo("/stomp");
        assertThat(endpoint.isWebSocket()).isTrue();
        assertThat(endpoint.toString()).isEqualTo("ws://localhost:8080/stomp");
    }

    @Test
    void apply_default_ports_and_websocket_path() {
        assertThat(StompEndpoint.parse("tcp://localhost").port()).isEqualTo(61613);

        StompEndpoint secure = StompEndpoint.parse("wss://example.com");
        assertThat(secure.port()).isEqualTo(443);
        assertThat(secure.path()).isEqualTo("/");
        assertThat(secure.isSecure()).isTrue();
    }

    @Test
    void reject_tcp_endpoint_path() {
        assertThatThrownBy(() -> StompEndpoint.parse("tcp://localhost:61613/stomp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot have a path");
    }

    @Test
    void reject_unsupported_scheme() {
        assertThatThrownBy(() -> StompEndpoint.parse("http://localhost:8080/stomp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }
}
