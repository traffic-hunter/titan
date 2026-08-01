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

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.net.TlsSide;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class TitanClientBuilderTest {

    @Test
    void builds_native_client_with_session_and_runtime_configuration() {
        TitanClient client = TitanClient.builder()
                .worker(1)
                .host("localhost")
                .port(61614)
                .session(StompSessionOption.builder()
                        .login("user")
                        .passcode("secret")
                        .virtualHost("titan")
                        .heartbeatX(2000L)
                        .heartbeatY(3000L)
                        .maxFrameLength(131072)
                        .build())
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        assertThat(client).isInstanceOf(TitanStompClient.class);
        ClientConfiguration configuration = ((TitanStompClient) client).option();
        assertThat(configuration.host()).isEqualTo("localhost");
        assertThat(configuration.port()).isEqualTo(61614);
        assertThat(configuration.login()).isEqualTo("user");
        assertThat(configuration.passcode()).isEqualTo("secret");
        assertThat(configuration.virtualHost()).isEqualTo("titan");
        assertThat(configuration.heartbeatX()).isEqualTo(2000L);
        assertThat(configuration.heartbeatY()).isEqualTo(3000L);
        assertThat(configuration.maxFrameLength()).isEqualTo(131072);
        assertThat(configuration.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void builds_vertx_client_without_native_event_loop_groups() {
        TitanClient client = TitanClient.builder()
                .implementation(TitanClient.Implementation.VERTX)
                .build();

        try {
            assertThat(client.name()).isEqualTo("vertx");
        } finally {
            client.shutdown(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void rejects_non_positive_worker_count() {
        assertThatThrownBy(() -> TitanClient.builder().worker(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workers must be greater than zero");
    }

    @Test
    void stores_tls_context_in_native_client_configuration() {
        TlsContext tlsContext = mock(TlsContext.class);
        when(tlsContext.side()).thenReturn(TlsSide.CLIENT);

        TitanClient client = TitanClient.builder()
                .tls(tlsContext)
                .build();

        try {
            assertThat(((TitanStompClient) client).option().tlsContext()).isSameAs(tlsContext);
        } finally {
            client.shutdown(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void rejects_titan_tls_context_for_vertx_client() {
        TlsContext tlsContext = mock(TlsContext.class);

        assertThatThrownBy(() -> TitanClient.builder()
                .implementation(TitanClient.Implementation.VERTX)
                .tls(tlsContext)
                .build())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Vert.x client TLS is not supported by Titan's TLS context");
    }

    @Test
    void stores_websocket_path_in_client_configuration() {
        TitanClient client = TitanClient.builder()
                .webSocket("/stomp")
                .build();

        try {
            assertThat(((TitanStompClient) client).option().webSocketPath()).isEqualTo("/stomp");
        } finally {
            client.shutdown(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void rejects_invalid_websocket_path_when_building_client() {
        assertThatThrownBy(() -> TitanClient.builder().webSocket("stomp").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WebSocket path must start with '/'");
    }
}
