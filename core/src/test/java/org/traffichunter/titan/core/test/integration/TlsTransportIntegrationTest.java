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
import org.traffichunter.titan.core.channel.Channel;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.net.JdkTlsContext;
import org.traffichunter.titan.core.net.TlsClientAuth;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.net.TlsOptions;
import org.traffichunter.titan.core.net.TlsSide;
import org.traffichunter.titan.core.net.TlsVersion;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.websocket.WebSocketClient;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class TlsTransportIntegrationTest {

    private static final String PASSWORD = "changeit";

    private InetServer server;
    private InetClient client;
    private WebSocketClient webSocketClient;

    @Test
    void reject_tls_context_configured_for_opposite_transport_side() throws Exception {
        Path keyStore = testKeyStore();

        assertThatThrownBy(() -> InetClient.open(EventLoopGroups.group(1))
                .tls(context(TlsSide.SERVER, keyStore)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-side");

        assertThatThrownBy(() -> InetServer.open(EventLoopGroups.group(1, 1))
                .tls(context(TlsSide.CLIENT, keyStore)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server-side");
    }

    @Test
    @Timeout(10)
    void remove_created_channel_when_tls_handler_creation_fails() throws Exception {
        EventLoopGroups groups = EventLoopGroups.group(1);
        TlsContext tls = mock(TlsContext.class);
        IllegalStateException failure = new IllegalStateException("TLS handler creation failed");
        when(tls.side()).thenReturn(TlsSide.CLIENT);
        when(tls.newHandler("localhost", 1)).thenThrow(failure);
        client = InetClient.open(groups).tls(tls);
        AtomicReference<Channel> created = new AtomicReference<>();
        client.onChannel(created::set);
        client.start();

        Promise<NetChannel> result = client.connect("localhost", 1, 1, TimeUnit.SECONDS);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.error()).isSameAs(failure);
        // Drain previously submitted cleanup before inspecting the registry.
        groups.secondaryGroup().submit(() -> { }).get(3, TimeUnit.SECONDS);
        assertThat(client.channels()).isEmpty();
        assertThat(created.get()).isNotNull().satisfies(channel -> {
            assertThat(channel.isClosed()).isTrue();
            assertThat(channel.isOpen()).isFalse();
        });
    }

    @AfterEach
    void tearDown() {
        if (webSocketClient != null && !webSocketClient.isShutdown()) {
            webSocketClient.shutdown();
        }
        if (client != null && !client.isShutdown()) {
            client.shutdown();
        }
        if (server != null && !server.isShutdown()) {
            server.shutdown();
        }
    }

    @Test
    @Timeout(10)
    void complete_connection_and_exchange_payload_after_tls_handshake() throws Exception {
        Path keyStore = testKeyStore();
        LinkedBlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();

        server = InetServer.open(EventLoopGroups.group(1, 1))
                .tls(context(TlsSide.SERVER, keyStore))
                .onChannel(channel -> channel.chain().add(echo(serverMessages)));
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);

        int port = ((InetSocketAddress) server.localAddress()).getPort();
        client = InetClient.open(EventLoopGroups.group(1))
                .tls(context(TlsSide.CLIENT, keyStore))
                .onChannel(channel -> channel.chain().add(capture(clientMessages)));
        client.start();

        NetChannel channel = client.connect("localhost", port, 5, TimeUnit.SECONDS)
                .get(5, TimeUnit.SECONDS);

        assertThat(channel.isConnected()).isTrue();
        assertThat(channel.isActive()).isTrue();

        client.send(Buffer.heap().alloc("hello tls")).get(5, TimeUnit.SECONDS);

        assertThat(serverMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("hello tls");
        assertThat(clientMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("echo:hello tls");
    }

    @Test
    @Timeout(10)
    void connect_when_server_requires_client_certificate() throws Exception {
        Path keyStore = testKeyStore();
        LinkedBlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();

        server = InetServer.open(EventLoopGroups.group(1, 1))
                .tls(context(TlsSide.SERVER, TlsClientAuth.NEED, keyStore))
                .onChannel(channel -> channel.chain().add(echo(serverMessages)));
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);

        int port = ((InetSocketAddress) server.localAddress()).getPort();
        client = InetClient.open(EventLoopGroups.group(1))
                .tls(context(TlsSide.CLIENT, keyStore));
        client.start();

        NetChannel channel = client.connect("localhost", port, 5, TimeUnit.SECONDS)
                .get(5, TimeUnit.SECONDS);
        client.send(Buffer.heap().alloc("mutual tls")).get(5, TimeUnit.SECONDS);

        assertThat(channel.isConnected()).isTrue();
        assertThat(serverMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("mutual tls");
    }

    @Test
    @Timeout(10)
    void exchange_websocket_payload_over_tls() throws Exception {
        Path keyStore = testKeyStore();
        LinkedBlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();

        server = InetServer.open(EventLoopGroups.group(1, 1))
                .tls(context(TlsSide.SERVER, keyStore))
                .upgradeWebSocket("/stomp")
                .onChannel(channel -> channel.chain().add(echo(serverMessages)));
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);

        int port = ((InetSocketAddress) server.localAddress()).getPort();
        webSocketClient = InetClient.open(EventLoopGroups.group(1))
                .tls(context(TlsSide.CLIENT, keyStore))
                .upgradeWebSocket(Protocol.STOMP, "/stomp");
        webSocketClient.start();

        WebSocketChannel channel = webSocketClient.connect("localhost", port, 5, TimeUnit.SECONDS)
                .get(5, TimeUnit.SECONDS);
        channel.chain().add(capture(clientMessages));

        webSocketClient.send(Buffer.heap().alloc("secure websocket")).get(5, TimeUnit.SECONDS);

        assertThat(serverMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("secure websocket");
        assertThat(clientMessages.poll(5, TimeUnit.SECONDS)).isEqualTo("echo:secure websocket");
    }

    private static JdkTlsContext context(TlsSide side, Path keyStore) {
        return context(side, TlsClientAuth.NONE, keyStore);
    }

    private static JdkTlsContext context(TlsSide side, TlsClientAuth clientAuth, Path keyStore) {
        return new JdkTlsContext(TlsOptions.builder()
                .side(side)
                .versions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                .clientAuth(clientAuth)
                .keyStore(keyStore, "PKCS12", PASSWORD, PASSWORD)
                .verifyHostname(side == TlsSide.CLIENT)
                .build());
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

    private static Path testKeyStore() throws Exception {
        URL resource = TlsTransportIntegrationTest.class.getResource("/tls/titan-test.p12");
        if (resource == null) {
            throw new AssertionError("Missing TLS test key store");
        }
        return Path.of(resource.toURI());
    }
}
