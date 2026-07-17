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

        server = InetServer.open(EventLoopGroups.group(1))
                .upgradeWebsocket("/stomp")
                .onChannel(channel -> channel.chain().add(echo(serverMessages)));
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);

        int port = ((InetSocketAddress) server.localAddress()).getPort();
        client = InetClient.open(EventLoopGroups.group(1))
                .upgradeWebsocket(Protocol.STOMP, "/stomp");
        client.start();

        WebSocketChannel channel = client.connect("localhost", port, 5, TimeUnit.SECONDS)
                .get(5, TimeUnit.SECONDS);
        channel.chain().add(capture(clientMessages));

        client.send(Buffer.alloc("hello websocket")).get(5, TimeUnit.SECONDS);

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
                    channel.writeAndFlush(Buffer.alloc("echo:" + message));
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
