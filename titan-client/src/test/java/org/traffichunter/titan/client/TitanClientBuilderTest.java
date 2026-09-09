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

        assertThat(client).isInstanceOf(DefaultTitanClient.class);
        DefaultTitanClient defaultClient = (DefaultTitanClient) client;
        assertThat(defaultClient.driver()).isInstanceOf(TitanStompClientDriver.class);
        ClientConfiguration configuration = defaultClient.configuration();
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
            assertThat(((DefaultTitanClient) client).configuration().tlsContext()).isSameAs(tlsContext);
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
            assertThat(((DefaultTitanClient) client).configuration().webSocketPath()).isEqualTo("/stomp");
        } finally {
            client.shutdown(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void normalizes_websocket_path_when_building_client() {
        TitanClient relativePathClient = TitanClient.builder().webSocket("stomp").build();
        TitanClient rootPathClient = TitanClient.builder().webSocket("").build();

        try {
            assertThat(((DefaultTitanClient) relativePathClient).configuration().webSocketPath())
                    .isEqualTo("/stomp");
            assertThat(((DefaultTitanClient) rootPathClient).configuration().webSocketPath())
                    .isEqualTo("/");
        } finally {
            relativePathClient.shutdown(5, java.util.concurrent.TimeUnit.SECONDS);
            rootPathClient.shutdown(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
