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
package org.traffichunter.titan.core.test.implementation;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.net.HttpRequest;

/**
 * @author yun
 */
public class HttpRequestTest {

    @Test
    void http_request_header_test() {
        HttpRequest request = new HttpRequest();
        request.uri("/titan")
                .header("Host", "localhost:8080")
                .header("Upgrade", "websocket")
                .header("Connection", "Upgrade")
                .header("Sec-WebSocket-Key", "123")
                .header("Sec-WebSocket-Version", "13");

        Assertions.assertThat(request.header("Sec-WebSocket-Key")).isEqualTo("123");
        Assertions.assertThat(request.header("Sec-WebSocket-Version")).isEqualTo("13");
        Assertions.assertThat(request.toString()).isEqualTo("""
                GET /titan HTTP/1.1\r
                Host: localhost:8080\r
                Upgrade: websocket\r
                Connection: Upgrade\r
                Sec-WebSocket-Key: 123\r
                Sec-WebSocket-Version: 13\r
                \r
                """);
    }

    @Test
    void parse_http_request_test() {
        HttpRequest request = HttpRequest.parse("""
                GET /titan HTTP/1.1\r
                Host: localhost:8080\r
                Upgrade: websocket\r
                Connection: keep-alive, Upgrade\r
                Sec-WebSocket-Key: 123\r
                Sec-WebSocket-Version: 13\r
                \r
                """);

        Assertions.assertThat(request.method()).isEqualTo("GET");
        Assertions.assertThat(request.uri()).isEqualTo("/titan");
        Assertions.assertThat(request.header("host")).isEqualTo("localhost:8080");
        Assertions.assertThat(request.header("SEC-WEBSOCKET-KEY")).isEqualTo("123");
        Assertions.assertThat(request.toString()).isEqualTo("""
                GET /titan HTTP/1.1\r
                Host: localhost:8080\r
                Upgrade: websocket\r
                Connection: keep-alive, Upgrade\r
                Sec-WebSocket-Key: 123\r
                Sec-WebSocket-Version: 13\r
                \r
                """);
    }
}
