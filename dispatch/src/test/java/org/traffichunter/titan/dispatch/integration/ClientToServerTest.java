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
package org.traffichunter.titan.dispatch.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClientToServerTest {

    private final DispatcherQueue rq = DispatcherQueue.create(Destination.create("/route/test"), 1001);
    private InetServer server;
    private int port;

    private static final Logger log = LoggerFactory.getLogger(ClientToServerTest.class);

    @BeforeEach
    void setUp() throws Exception {
        server = InetServer.open(EventLoopGroups.group(1, 1))
                .option(InetServerOption.builder().build())
                .onChannel(ctx -> ctx.chain()
                        .add(new LineFrameChannelDecoder())
                        .add(new TestChannelInboundHandler()));

        server.start();

        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);
        InetSocketAddress localAddress = (InetSocketAddress) server.localAddress();
        port = localAddress.getPort();
        log.info("Server started successfully. port={}", port);
    }

    @AfterEach
    void refresh() {
        rq.clear();
        server.shutdown();
    }

    @Order(1)
    @Test
    void client_to_server_single_send_test() throws ExecutionException, InterruptedException {

        InetClient client = InetClient.open(EventLoopGroups.group())
                .onChannel(channel -> channel.chain());

        client.start();
        NetChannel channel = client.connect("localhost", port).get();

        assertThat(channel.isConnected()).isTrue();
        assertThat(channel.isActive()).isTrue();

        client.send(Buffer.heap().alloc("hello\n".getBytes(StandardCharsets.UTF_8))).addListener(future -> {
            if(future.isSuccess()) {
                log.info("Send successfully");
            }
        });

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(rq.size()).isEqualTo(1));

        client.shutdown();
    }

    @Order(2)
    @Test
    void client_to_server_multiple_send_test() throws Exception {
        int count = 100;

        ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2, r -> new Thread(r, "TestThread"));

        InetClient client = InetClient.open(EventLoopGroups.group())
                .onChannel(channel -> channel.chain());

        client.start();
        client.connect("localhost", port).get();

        for (int i = 0; i < count; i++) {
            es.execute(() -> {
                try {
                    client.send(Buffer.heap().alloc("hello\n".getBytes(StandardCharsets.UTF_8))).addListener(future -> {
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

    private class TestChannelInboundHandler implements ChannelInBoundHandler {

        @Override
        public void sparkChannelRead(@NonNull NetChannel channel, @NonNull Buffer buffer, @NonNull ChannelInBoundHandlerChain chain) {
            final Message msg = Message.builder()
                    .destination(Destination.create("/route/test"))
                    .body(buffer.getBytes())
                    .producerId(IdGenerator.uuid())
                    .createdAt(Instant.now())
                    .build();

            log.info("msg = {}", msg.toString());

            rq.enqueue(msg);
        }
    }
}
