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
package org.traffichunter.titan.core.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandlerChainImpl;
import org.traffichunter.titan.core.channel.ChannelSecondaryIOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.net.URL;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises two real JDK TLS engines through Titan's handler and event-loop contracts.
 *
 * @author yun
 */
class JdkTlsHandlerIntegrationTest {

    private static final String PASSWORD = "changeit";

    @Test
    @Timeout(15)
    void handshake_exchange_application_data_and_close_cleanly() throws Exception {
        Path keyStore = testKeyStore();
        Endpoint client = endpoint("tls-client", context(TlsSide.CLIENT, keyStore), "localhost", 61614);
        Endpoint server = endpoint("tls-server", context(TlsSide.SERVER, keyStore), "localhost", 61614);

        try {
            ChannelPromise clientHandshake = client.handler.handshake(client.channel);
            ChannelPromise serverHandshake = server.handler.handshake(server.channel);

            exchangeUntil(
                    () -> clientHandshake.isDone() && serverHandshake.isDone(),
                    client,
                    server
            );

            assertThat(clientHandshake.isSuccess()).isTrue();
            assertThat(serverHandshake.isSuccess()).isTrue();
            assertThat(client.handler.isCompletedHandshake()).isTrue();
            assertThat(server.handler.isCompletedHandshake()).isTrue();
            assertThat(client.handler.session().getProtocol()).isIn("TLSv1.3", "TLSv1.2");
            assertThat(server.handler.session().getProtocol()).isEqualTo(client.handler.session().getProtocol());

            client.write("client-to-server");
            exchangeUntil(() -> !server.plainTexts.isEmpty(), client, server);
            assertPlainText(server, "client-to-server");

            server.write("server-to-client");
            exchangeUntil(() -> !client.plainTexts.isEmpty(), client, server);
            assertPlainText(client, "server-to-client");

            ChannelPromise closeResult = client.handler.close(client.channel);
            exchangeUntil(
                    () -> closeResult.isDone() && client.handler.isClosed() && server.handler.isClosed(),
                    client,
                    server
            );

            assertThat(closeResult.isSuccess()).isTrue();
            assertThat(client.handler.isClosed()).isTrue();
            assertThat(server.handler.isClosed()).isTrue();
        } finally {
            client.close();
            server.close();
        }
    }

    private static JdkTlsContext context(TlsSide side, Path keyStore) {
        return new JdkTlsContext(new TlsOptions(
                side,
                new TlsVersion[]{TlsVersion.TLS_1_3, TlsVersion.TLS_1_2},
                TlsClientAuth.NONE,
                keyStore,
                "PKCS12",
                PASSWORD,
                PASSWORD,
                side == TlsSide.CLIENT
        ));
    }

    private static Endpoint endpoint(String name, JdkTlsContext context, String peerHost, int peerPort) {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop(name);
        NetChannel channel = mock(NetChannel.class);
        NetChannel.Internal internal = mock(NetChannel.Internal.class);
        Queue<Buffer> encryptedRecords = new ConcurrentLinkedQueue<>();

        when(channel.eventLoop()).thenReturn(eventLoop);
        when(channel.internal()).thenReturn(internal);
        doAnswer(invocation -> {
            encryptedRecords.add(invocation.getArgument(0));
            return null;
        }).when(internal).write(any(Buffer.class));

        eventLoop.start();
        return new Endpoint(
                eventLoop,
                channel,
                (JdkTlsHandler) context.newHandler(peerHost, peerPort),
                encryptedRecords
        );
    }

    private static void exchangeUntil(BooleanSupplier completed, Endpoint first, Endpoint second) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!completed.getAsBoolean()) {
            boolean progressed = transfer(first, second);
            progressed |= transfer(second, first);

            if (completed.getAsBoolean()) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("TLS exchange did not complete before timeout");
            }
            if (!progressed) {
                Thread.sleep(1);
            }
        }
    }

    private static boolean transfer(Endpoint source, Endpoint target) throws Exception {
        Buffer encrypted = source.encryptedRecords.poll();
        if (encrypted == null) {
            return false;
        }

        try {
            Promise<Void> result = target.eventLoop.submit(() -> {
                Buffer plainText = target.handler.decode(target.channel, encrypted);
                if (plainText != null) {
                    target.plainTexts.add(plainText);
                }
            });
            result.get(2, TimeUnit.SECONDS);
            return true;
        } finally {
            encrypted.release();
        }
    }

    private static void assertPlainText(Endpoint endpoint, String expected) {
        Buffer plainText = endpoint.plainTexts.remove();
        try {
            assertThat(plainText.toString()).isEqualTo(expected);
        } finally {
            plainText.release();
        }
    }

    private static Path testKeyStore() throws Exception {
        URL resource = JdkTlsHandlerIntegrationTest.class.getResource("/tls/titan-test.p12");
        if (resource == null) {
            throw new AssertionError("Missing TLS test key store");
        }
        return Path.of(resource.toURI());
    }

    private static final class Endpoint implements AutoCloseable {

        private final ChannelSecondaryIOEventLoop eventLoop;
        private final NetChannel channel;
        private final JdkTlsHandler handler;
        private final Queue<Buffer> encryptedRecords;
        private final Queue<Buffer> plainTexts = new ConcurrentLinkedQueue<>();

        private Endpoint(
                ChannelSecondaryIOEventLoop eventLoop,
                NetChannel channel,
                JdkTlsHandler handler,
                Queue<Buffer> encryptedRecords
        ) {
            this.eventLoop = eventLoop;
            this.channel = channel;
            this.handler = handler;
            this.encryptedRecords = encryptedRecords;
        }

        private void write(String value) throws Exception {
            Promise<Void> result = eventLoop.submit(() -> {
                ChannelOutBoundHandlerChainImpl chain =
                        new ChannelOutBoundHandlerChainImpl(mock(ChannelOutBoundHandler.class));
                handler.sparkChannelWrite(channel, Buffer.alloc(value), chain);
                channel.internal().flush();
            });
            result.get(2, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            release(encryptedRecords);
            release(plainTexts);
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }

        private static void release(Queue<Buffer> buffers) {
            Buffer buffer;
            while ((buffer = buffers.poll()) != null) {
                buffer.release();
            }
        }
    }
}
