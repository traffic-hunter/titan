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
package org.traffichunter.titan.core.transport.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.transport.ClientException;

@EnableStompServer
class StompIntegrationTest {

    private static EventLoopGroups clientGroups() {
        return EventLoopGroups.group(1, 1);
    }

    @Test
    void stomp_server_started_test(StompTestServer testServer) {
        assertThat(testServer.server()).isNotNull();
        assertThat(testServer.server().isStart()).isTrue();
    }

    @Test
    void stomp_client_can_connect_to_server_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.builder()
                .group(clientGroups())
                .build();

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);
            assertThat(client.remoteAddress()).isNotNull();
        } finally {
            client.shutdown();
        }
    }

    @RepeatedTest(10)
    void stomp_ping_pong_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.builder()
                .group(clientGroups())
                .build();

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(10, TimeUnit.SECONDS);
            client.send(StompFrame.PING).get(10, TimeUnit.SECONDS);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void stomp_send_before_connect_should_fail_test() {
        StompClient client = StompClient.builder()
                .group(clientGroups())
                .build();

        try {
            client.start();

            assertThatThrownBy(() -> client.send(StompFrame.PING).get(3, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ClientException.class);
        } finally {
            client.shutdown();
        }
    }

    @RepeatedTest(5)
    void stomp_reconnect_cycle_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.builder()
                .group(clientGroups())
                .build();

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(10, TimeUnit.SECONDS);
            client.send(StompFrame.PING).get(10, TimeUnit.SECONDS);

            client.shutdown();
            assertThat(client.isClosed()).isTrue();
        } finally {
            if (!client.isClosed()) {
                client.shutdown();
            }
        }
    }
}
