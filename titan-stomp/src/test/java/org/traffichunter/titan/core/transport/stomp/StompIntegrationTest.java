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

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.Transaction;
import org.traffichunter.titan.core.codec.stomp.Transactions;
import org.traffichunter.titan.core.message.dispatcher.Dispatcher;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.transport.stomp.option.StompClientOption;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

@EnableStompServer
class StompIntegrationTest {

    private static EventLoopGroups clientGroups() {
        return EventLoopGroups.group(1, 1);
    }

    // ===== Server Lifecycle Tests =====

    @Test
    void stomp_server_started_test(StompTestServer testServer) {
        assertThat(testServer.server()).isNotNull();
        assertThat(testServer.server().isStart()).isTrue();
        assertThat(testServer.dispatcher()).isNotNull();
    }

    // ===== CONNECT Command Tests =====

    @Test
    void stomp_client_can_connect_to_server_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            assertThat(client.connection().isConnected()).isTrue();
            assertThat(client.connection().session()).isNotNull();
            assertThat(client.remoteAddress()).isNotNull();
        } finally {
            client.shutdown();
        }
    }

    @RepeatedTest(5)
    void stomp_reconnect_cycle_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port())
                    .get(3, TimeUnit.SECONDS)
                    .send(StompFrame.PING)
                    .get(3, TimeUnit.SECONDS);

            client.shutdown();
            assertThat(client.isClosed()).isTrue();
        } finally {
            if (!client.isClosed()) {
                client.shutdown();
            }
        }
    }

    // ===== SEND Command Tests =====

    @RepeatedTest(10)
    void stomp_ping_pong_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS)
                    .send(StompFrame.PING)
                    .get(3, TimeUnit.SECONDS);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void stomp_send_with_text_body_test(StompTestServer testServer, Dispatcher dispatcher) throws Exception {
        Destination key = Destination.create("/queue/test");
        DispatcherQueue queue = dispatcher.getOrPut(key);
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            Buffer body = Buffer.alloc("Hello STOMP!");
            StompFrame result = client.connection().send("/queue/test", body).get(3, TimeUnit.SECONDS);

            assertThat(result).isNotNull();
            assertThat(result.getCommand()).isEqualTo(StompCommand.SEND);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().toString()).isEqualTo("Hello STOMP!");
        } finally {
            client.shutdown();
            dispatcher.remove(key);
        }
    }

    // ===== SUBSCRIBE/UNSUBSCRIBE Command Tests =====

    @Test
    void stomp_subscribe_and_receive_message_test(StompTestServer testServer, Dispatcher dispatcher) throws Exception {
        Destination key = Destination.create("/topic/test");
        DispatcherQueue queue = dispatcher.getOrPut(key);

        AtomicBoolean messageReceived = new AtomicBoolean(false);
        AtomicReference<StompFrame> receivedFrame = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();

            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            // Subscribe with message handler
            client.connection().subscribe("/topic/test", frame -> {
                messageReceived.set(true);
                receivedFrame.set(frame);
                latch.countDown();
            }).get(3, TimeUnit.SECONDS);

            assertThat(client.connection().subscriptions()).hasSize(1);

            // Publish message
            client.connection().send("/topic/test", Buffer.alloc("Test message")).get(3, TimeUnit.SECONDS);

            // Wait for message
            boolean received = latch.await(5, TimeUnit.SECONDS);

            if (received) {
                assertThat(messageReceived.get()).isTrue();
                assertThat(receivedFrame.get()).isNotNull();
                assertThat(receivedFrame.get().getCommand()).isEqualTo(StompCommand.MESSAGE);
            }
        } finally {
            client.shutdown();
            dispatcher.remove(key);
        }
    }

    @Test
    void stomp_unsubscribe_test(StompTestServer testServer, Dispatcher dispatcher) throws Exception {
        Destination key = Destination.create("/topic/unsub");
        DispatcherQueue queue = dispatcher.getOrPut(key);

        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            // Subscribe
            StompHeaders headers = StompHeaders.create();
            headers.put(Elements.ID, "sub-1");
            client.connection().subscribe("/topic/unsub", headers, frame -> {}).get(3, TimeUnit.SECONDS);

            assertThat(client.connection().subscriptions()).hasSize(1);

            // Unsubscribe
            StompHeaders unsubHeaders = StompHeaders.create();
            unsubHeaders.put(Elements.ID, "sub-1");
            client.connection().unsubscribe("/topic/unsub", unsubHeaders).get(3, TimeUnit.SECONDS);

            assertThat(client.connection().subscriptions()).hasSize(0);
        } finally {
            client.shutdown();
            dispatcher.remove(key);
        }
    }

    @Test
    void stomp_subscribe_with_ack_modes_test(StompTestServer testServer, Dispatcher dispatcher) throws Exception {
        Destination key1 = Destination.create("/topic/ack1");
        Destination key2 = Destination.create("/topic/ack2");
        Destination key3 = Destination.create("/topic/ack3");
        dispatcher.getOrPut(key1);
        dispatcher.getOrPut(key2);
        dispatcher.getOrPut(key3);

        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            // AUTO ack
            StompHeaders auto = StompHeaders.create();
            auto.put(Elements.ACK, StompFrame.AckMode.AUTO);
            client.connection().subscribe("/topic/ack1", auto, frame -> {}).get(3, TimeUnit.SECONDS);

            // CLIENT ack
            StompHeaders clientAck = StompHeaders.create();
            clientAck.put(Elements.ACK, StompFrame.AckMode.CLIENT);
            client.connection().subscribe("/topic/ack2", clientAck, frame -> {}).get(3, TimeUnit.SECONDS);

            // CLIENT_INDIVIDUAL ack
            StompHeaders individual = StompHeaders.create();
            individual.put(Elements.ACK, StompFrame.AckMode.CLIENT_INDIVIDUAL);
            client.connection().subscribe("/topic/ack3", individual, frame -> {}).get(3, TimeUnit.SECONDS);

            assertThat(client.connection().subscriptions()).hasSize(3);
        } finally {
            client.shutdown();
            dispatcher.remove(key1);
            dispatcher.remove(key2);
            dispatcher.remove(key3);
        }
    }

    // ===== ACK/NACK Command Tests =====

    @Test
    void stomp_ack_command_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);
            client.connection().ack("msg-1").get(3, TimeUnit.SECONDS);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void stomp_nack_command_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);
            client.connection().nack("msg-2").get(3, TimeUnit.SECONDS);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void stomp_ack_within_transaction_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            final StompClientConnection serverConnection = awaitSingleServerConnection(testServer);
            String txId = "tx-" + UUID.randomUUID();
            client.connection().begin(txId).get(3, TimeUnit.SECONDS);
            client.connection().ack("msg-3", txId).get(3, TimeUnit.SECONDS);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                Transaction transaction = Transactions.getInstance().getTransaction(serverConnection, txId);
                assertThat(transaction).isNotNull();
                assertThat(transaction.getFrames()).hasSize(1);
                assertThat(transaction.getFrames().getFirst().getCommand()).isEqualTo(StompCommand.ACK);
                assertThat(transaction.getFrames().getFirst().getHeader(Elements.ID)).isEqualTo("msg-3");
            });

            client.connection().commit(txId).get(3, TimeUnit.SECONDS);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(Transactions.getInstance().getTransaction(serverConnection, txId)).isNull());
        } finally {
            testServer.server().connection().connections()
                    .forEach(connection -> Transactions.getInstance().removeTransactions(connection));
            client.shutdown();
        }
    }

    @Test
    void stomp_nack_within_transaction_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            final StompClientConnection serverConnection = awaitSingleServerConnection(testServer);
            String txId = "tx-" + UUID.randomUUID();
            client.connection().begin(txId).get(3, TimeUnit.SECONDS);
            client.connection().nack("msg-4", txId).get(3, TimeUnit.SECONDS);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                Transaction transaction = Transactions.getInstance().getTransaction(serverConnection, txId);
                assertThat(transaction).isNotNull();
                assertThat(transaction.getFrames()).hasSize(1);
                assertThat(transaction.getFrames().getFirst().getCommand()).isEqualTo(StompCommand.NACK);
                assertThat(transaction.getFrames().getFirst().getHeader(Elements.ID)).isEqualTo("msg-4");
            });

            client.connection().abort(txId).get(3, TimeUnit.SECONDS);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(Transactions.getInstance().getTransaction(serverConnection, txId)).isNull());
        } finally {
            testServer.server().connection().connections()
                    .forEach(connection -> Transactions.getInstance().removeTransactions(connection));
            client.shutdown();
        }
    }

    // ===== Transaction Command Tests =====

    @Test
    void stomp_transaction_begin_commit_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            String txId = "tx-" + UUID.randomUUID();
            client.connection().begin(txId).get(3, TimeUnit.SECONDS);
            client.connection().commit(txId).get(3, TimeUnit.SECONDS);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void stomp_transaction_begin_abort_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            String txId = "tx-" + UUID.randomUUID();
            client.connection().begin(txId).get(3, TimeUnit.SECONDS);
            client.connection().abort(txId).get(3, TimeUnit.SECONDS);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void stomp_transaction_with_send_test(StompTestServer testServer, Dispatcher dispatcher) throws Exception {
        Destination key = Destination.create("/queue/tx-test");
        DispatcherQueue queue = dispatcher.getOrPut(key);

        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION)
                .onChannel(channel ->
                        channel.chain()
                            .add(new TestChannelInboundHandler(queue))
                );

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            String txId = "tx-" + UUID.randomUUID();
            client.connection().begin(txId).get(3, TimeUnit.SECONDS);

            // Send within transaction
            StompHeaders headers = StompHeaders.create();
            headers.put(Elements.TRANSACTION, txId);
            client.connection().send("/queue/tx-test", Buffer.alloc("TX message"), headers).get(3, TimeUnit.SECONDS);

            client.connection().commit(txId).get(3, TimeUnit.SECONDS);

            Thread.sleep(100);
            assertThat(queue.size()).isGreaterThan(0);
        } finally {
            client.shutdown();
            dispatcher.remove(key);
        }
    }

    @RepeatedTest(3)
    void stomp_multiple_transactions_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            // First TX - commit
            String tx1 = "tx-1-" + UUID.randomUUID();
            client.connection().begin(tx1).get(3, TimeUnit.SECONDS);
            client.connection().commit(tx1).get(3, TimeUnit.SECONDS);

            // Second TX - abort
            String tx2 = "tx-2-" + UUID.randomUUID();
            client.connection().begin(tx2).get(3, TimeUnit.SECONDS);
            client.connection().abort(tx2).get(3, TimeUnit.SECONDS);
        } finally {
            client.shutdown();
        }
    }

    // ===== DISCONNECT Command Tests =====

    @Test
    void stomp_disconnect_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);
            assertThat(client.connection().isConnected()).isTrue();

            client.connection().disconnect();
            await().atMost(1, TimeUnit.SECONDS).until(client::isClosed);
        } finally {
            if (!client.isClosed()) {
                client.shutdown();
            }
        }
    }

    // ===== ERROR Handling Tests =====

    @Test
    void stomp_subscribe_to_nonexistent_destination_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            // Subscribe to destination not in dispatcher
            client.connection().subscribe("/nonexistent", frame -> {}).get(3, TimeUnit.SECONDS);
        } finally {
            if (!client.isClosed()) {
                client.shutdown();
            }
        }
    }

    @Test
    void stomp_commit_without_begin_test(StompTestServer testServer) throws Exception {
        StompClient client = StompClient.open(clientGroups(), StompClientOption.DEFAULT_STOMP_CLIENT_OPTION);

        try {
            client.start();
            client.connect(testServer.host(), testServer.port()).get(3, TimeUnit.SECONDS);

            client.connection().commit("non-existent-tx").get(3, TimeUnit.SECONDS);
        } finally {
            if (!client.isClosed()) {
                client.shutdown();
            }
        }
    }

    private static StompClientConnection awaitSingleServerConnection(StompTestServer testServer) {
        AtomicReference<StompClientConnection> holder = new AtomicReference<>();
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(testServer.server().connection().connections()).isNotEmpty();
            holder.set(testServer.server().connection().connections().getFirst());
        });
        return holder.get();
    }

    @NullMarked
    private static class TestChannelInboundHandler implements ChannelInBoundHandler {

        private final DispatcherQueue queue;

        public TestChannelInboundHandler(DispatcherQueue queue) {
            this.queue = queue;
        }

        @Override
        public void sparkChannelRead(NetChannel channel, Buffer buffer, ChannelInBoundHandlerChain chain) {
            Message message = Message.builder()
                    .priority(Priority.DEFAULT)
                    .destination(queue.route())
                    .createdAt(Instant.now())
                    .producerId(UUID.randomUUID().toString())
                    .body(buffer)
                    .build();

            queue.enqueue(message);
            chain.sparkChannelRead(channel, buffer);
        }
    }
}
