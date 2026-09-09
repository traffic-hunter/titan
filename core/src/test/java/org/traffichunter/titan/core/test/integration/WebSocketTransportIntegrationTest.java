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
package org.traffichunter.titan.core.test.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.websocket.WebSocketClient;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yun
 */
class WebSocketTransportIntegrationTest {

    private InetServer server;
    private WebSocketClient client;

    @AfterEach
    void tearDown() {
        if (client != null && !client.isShutdown()) {
            client.shutdown();
        }
        if (server != null && !server.isShutdown()) {
            server.shutdown();
        }
    }

    @Test
    @Timeout(10)
    void exchange_payload_after_websocket_upgrade() throws Exception {
        LinkedBlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();

        server = InetServer.open(EventLoopGroups.group(1, 1))
                .upgradeWebSocket("/stomp")
                .onChannel(channel -> channel.chain().add(echo(serverMessages)));
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);

        int port = ((InetSocketAddress) server.localAddress()).getPort();
        client = InetClient.open(EventLoopGroups.group(1))
                .upgradeWebSocket(Protocol.STOMP, "/stomp");
        client.start();

        WebSocketChannel channel = client.connect("localhost", port, 5, TimeUnit.SECONDS)
                .get(5, TimeUnit.SECONDS);
        channel.chain().add(capture(clientMessages));

        client.send(Buffer.heap().alloc("hello websocket")).get(5, TimeUnit.SECONDS);

        assertThat(serverMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("hello websocket");
        assertThat(clientMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("echo:hello websocket");
    }

    private static ChannelInBoundHandler echo(LinkedBlockingQueue<String> messages) {
        return new ChannelInBoundHandler() {
            @Override
            public void sparkChannelRead(
                    NetChannel channel,
                    Buffer buffer,
                    ChannelInBoundHandlerChain chain
            ) {
                try {
                    String message = buffer.toString();
                    messages.add(message);
                    channel.writeAndFlush(Buffer.heap().alloc("echo:" + message));
                } finally {
                    buffer.release();
                }
            }
        };
    }

    private static ChannelInBoundHandler capture(LinkedBlockingQueue<String> messages) {
        return new ChannelInBoundHandler() {
            @Override
            public void sparkChannelRead(
                    NetChannel channel,
                    Buffer buffer,
                    ChannelInBoundHandlerChain chain
            ) {
                try {
                    messages.add(buffer.toString());
                } finally {
                    buffer.release();
                }
            }
        };
    }
}
