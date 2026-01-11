package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousSocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.RoutingKey;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yungwang-o
 */
class InetServerTest {

    private static final DispatcherQueue rq = DispatcherQueue.create(RoutingKey.create("route.test"), 101);
    private static InetServer server;

    private static final Logger log = LoggerFactory.getLogger(InetServerTest.class);

    @BeforeAll
    static void setUp() {
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
    void inet_server_launch_test() {
        assertTrue(server.isStart());
    }

    @Test
    void inet_server_concurrent_connection_test() {

        ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        for (int i = 0; i < 100; i++) {
            es.submit(() -> {
                try (Socket socket = new Socket("localhost", 7777)) {
                    socket.getOutputStream().write("hello".getBytes());
                    socket.getOutputStream().flush();
                    Thread.sleep(50);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(rq.size()).isEqualTo(100));

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
            int recv = channel.read(buffer);
            if(recv > 0) {
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
            } else if(recv < 0) {
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