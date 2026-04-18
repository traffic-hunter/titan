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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
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

    private static final Logger log = LoggerFactory.getLogger(ClientToServerTest.class);

    @BeforeEach
    void setUp() throws InterruptedException {
        server = InetServer.open(EventLoopGroups.group(1))
                .option(InetServerOption.builder().build())
                .onChannel(ctx -> ctx.chain()
                        .add(new LineFrameChannelDecoder())
                        .add(new TestChannelInboundHandler()));

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
        server.shutdown();
    }

    @Order(1)
    @Test
    void client_to_server_single_send_test() throws ExecutionException, InterruptedException {

        InetClient client = InetClient.open(EventLoopGroups.group())
                .onChannel(channel -> channel.chain());

        client.start();
        client.connect("localhost", 7777).get();

        client.send(Buffer.alloc("hello\n".getBytes(StandardCharsets.UTF_8))).addListener(future -> {
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
        client.connect("localhost", 7777).get();

        for (int i = 0; i < count; i++) {
            es.execute(() -> {
                try {
                    client.send(Buffer.alloc("hello\n".getBytes(StandardCharsets.UTF_8))).addListener(future -> {
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
                    .priority(Priority.DEFAULT)
                    .body(buffer)
                    .producerId(IdGenerator.uuid())
                    .createdAt(Instant.now())
                    .build();

            log.info("msg = {}", msg.toString());

            rq.enqueue(msg);
        }
    }
}
