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

import java.net.StandardSocketOptions;
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

public class ClientToServerTest {

    private static final DispatcherQueue rq = DispatcherQueue.create(RoutingKey.create("route.test"), 1001);
    private static InetServer server;

    private static final Logger log = LoggerFactory.getLogger(ClientToServerTest.class);

    @BeforeAll
    static void setUp() throws InterruptedException {
        server = InetServer.builder()
                .group(EventLoopGroups.group(1))
                .option(StandardSocketOptions.SO_REUSEADDR, true)
                .channelHandler(ctx -> ctx.chain().add(new TestChannelInboundHandler()))
                .build();

        server.start();

        server.listen("localhost", 7777).addListener(future -> {
            if(future.isSuccess()) {
                log.info("Server started successfully");
            }
        });

        Thread.sleep(100);
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
    void single_client_to_server_test() {

        InetClient client = InetClient.builder()
                .group(EventLoopGroups.group())
                .option(StandardSocketOptions.SO_REUSEADDR, true)
                .option(StandardSocketOptions.SO_REUSEPORT, true)
                .channelHandler(channel -> channel.chain().add(new TestChannelInboundHandler()))
                .build();

        client.start();
        try {
            client.connect("localhost", 7777).get();
            client.send(Buffer.alloc("hello".getBytes(StandardCharsets.UTF_8))).addListener(future -> {
                if(future.isSuccess()) {
                    log.info("Send successfully");
                }
            });
        } catch (Exception e) {
            log.error("Error sending");
        } finally {
            Awaitility.await().atMost(Duration.ofSeconds(1))
                    .until(() -> rq.size() == 1);
        }

        client.shutdown();
    }

    @Test
    void client_to_server_send_test() throws Exception {
        int count = 100;

        ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2, r -> new Thread(r, "TestThread"));

        InetClient client = InetClient.builder()
                .group(EventLoopGroups.group())
                .option(StandardSocketOptions.SO_REUSEADDR, true)
                .option(StandardSocketOptions.SO_REUSEPORT, true)
                .channelHandler(channel -> channel.chain().add(new TestChannelInboundHandler()))
                .build();

        client.start();
        client.connect("localhost", 7777).get();

        for (int i = 0; i < count; i++) {
            es.execute(() -> {
                try {
                    client.send(Buffer.alloc("hello".getBytes(StandardCharsets.UTF_8))).addListener(future -> {
                        if(future.isSuccess()) {
                            log.info("Send successfully");
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(rq.size()).isEqualTo(count));

        client.shutdown();
        es.shutdown();
        es.close();
    }

    private static class TestChannelInboundHandler implements ChannelInBoundHandler {

        @Override
        public void sparkChannelConnecting(NetChannel channel) {

        }

        @Override
        public void sparkChannelAfterConnected(NetChannel channel) {

        }

        @Override
        public void sparkChannelRead(NetChannel channel, Buffer buffer) {
            int read = channel.read(buffer);
            if(read > 0) {
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
            } else {
                try {
                    channel.close();
                } finally {
                    buffer.release();
                }
            }
        }

        @Override
        public void sparkExceptionCaught(Throwable error) {

        }
    }
}
