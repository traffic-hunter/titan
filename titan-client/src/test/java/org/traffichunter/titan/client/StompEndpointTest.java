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
    void normalize_websocket_path_without_leading_slash() {
        StompEndpoint endpoint = StompEndpoint.webSocket("localhost", 8080, "stomp");

        assertThat(endpoint.path()).isEqualTo("/stomp");
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
