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
