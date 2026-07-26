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
