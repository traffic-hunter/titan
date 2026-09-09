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
package org.traffichunter.titan.core.spi.vertx;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.spi.NetworkServerEngineProvider;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class VertxStompWebSocketServerEngineProviderTest {

    @Test
    void expose_vertx_websocket_stomp_provider() {
        VertxStompWebSocketServerEngineProvider provider = new VertxStompWebSocketServerEngineProvider();

        assertThat(provider.transport()).isEqualTo("vertx-websocket");
        assertThat(provider.protocol()).isEqualTo("stomp");
    }

    @Test
    void discover_vertx_websocket_provider_with_service_loader() {
        assertThat(ServiceLoader.load(NetworkServerEngineProvider.class))
                .anyMatch(provider -> provider instanceof VertxStompWebSocketServerEngineProvider);
    }
}
