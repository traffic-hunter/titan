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
package org.traffichunter.titan.core.spi;

import org.junit.jupiter.api.Test;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.util.OptionKey;

import java.util.Map;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author yun
 */
class StompWebSocketServerEngineProviderTest {

    @Test
    void expose_websocket_stomp_provider() {
        StompWebSocketServerEngineProvider provider = new StompWebSocketServerEngineProvider();

        assertThat(provider.transport()).isEqualTo("websocket");
        assertThat(provider.protocol()).isEqualTo("stomp");
    }

    @Test
    void discover_websocket_provider_with_service_loader() {
        assertThat(ServiceLoader.load(NetworkServerEngineProvider.class))
                .anyMatch(provider -> provider instanceof StompWebSocketServerEngineProvider);
    }

    @Test
    void declare_stomp_and_socket_option_keys() {
        assertThat(new StompWebSocketServerEngineProvider().optionKeys())
                .extracting(OptionKey::name)
                .contains("path", "max-frame-length", "heartbeat-x", "secured", "reuse-address", "child-tcp-no-delay")
                .doesNotHaveDuplicates();
    }

    @Test
    void reject_boolean_option_other_than_true_or_false() {
        ServerSettings settings = new ServerSettings(
                "stomp-websocket", "websocket", "stomp", "localhost", 61613, 1, 1,
                Map.of(), Map.of(), Map.of("secured", "yes"), null
        );

        assertThatThrownBy(() -> new StompWebSocketServerEngineProvider().create(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value for secured: yes");
    }
}
