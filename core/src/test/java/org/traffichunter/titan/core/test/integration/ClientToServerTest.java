/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.test.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.RoutingKey;
import org.traffichunter.titan.core.util.buffer.Buffer;

@Disabled
public class ClientToServerTest {

    private static final DispatcherQueue rq = DispatcherQueue.create(RoutingKey.create("route.test"), 101);
    private static InetServer server;

    private static final Logger log = LoggerFactory.getLogger(ClientToServerTest.class);

    @BeforeAll
    static void setUp() {
        server = InetServer.open()
                .group(new ChannelPrimaryIOEventLoopGroup(), new ChannelSecondaryIOEventLoopGroup())
                .channelHandler(ctx -> ctx.chain().add(new TestChannelInboundFilter()))
                .start();

        server.listen("localhost", 7777).addListener(future -> {
            if(future.isSuccess()) {
                log.info("Server started successfully");
            }
        });
    }

    @AfterEach
    void refresh() {
        rq.clear();
    }

    @AfterAll
    static void tearDown() {
        server.shutdown();
    }

    @Test
    @Timeout(10)
    void single_client_to_server_test() throws Exception {
        InetClient client = InetClient.open("localhost", 7777)
                .onRead(buffer -> log.info("buffer = {}", buffer.length()))
                .onWrite(buffer -> {})
                .onConnect(channel -> {})
                .start();

        client.connect().get();

        client.send(Buffer.alloc("hello".getBytes(StandardCharsets.UTF_8)));

        Awaitility.await().atMost(Duration.ofSeconds(1))
                .until(() -> rq.size() == 1);
    }

    @Test
    @Timeout(10)
    void multi_client_to_server_test() {
        int count = 100;

        ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2, r -> new Thread(r, "TestThread"));

        for (int i = 0; i < count; i++) {
            es.execute(() -> {
                try {
                    InetClient client = InetClient.open("localhost", 7777)
                            .onRead(buffer -> {})
                            .onWrite(buffer -> {})
                            .onConnect(channel -> {})
                            .start();

                    client.connect().get();

                    client.send(Buffer.alloc("hello".getBytes(StandardCharsets.UTF_8))).get();
                    Thread.sleep(50);
                    client.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(rq.size()).isEqualTo(count));

        es.shutdown();
        es.close();
    }

    private static class TestChannelInboundFilter implements ChannelInBoundFilter {

        @Override
        public void doFilter(NetChannel channel, ChannelInboundFilterChain chain) throws Exception {
            Buffer buffer = Buffer.alloc(1024);

            channel.read(buffer);
            try {
                final Message msg = Message.builder()
                        .routingKey(RoutingKey.create("route.test"))
                        .priority(Priority.DEFAULT)
                        .body(buffer.getBytes())
                        .producerId(IdGenerator.uuid())
                        .createdAt(Instant.now())
                        .build();

                log.info("msg = {}", msg.toString());

                rq.enqueue(msg);
            } finally {
                buffer.release();
            }

            chain.doFilter(channel);
        }
    }
}
