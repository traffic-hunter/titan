package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.*;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.RoutingKey;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yungwang-o
 */
class InetServerImplTest {

    private static final DispatcherQueue rq = DispatcherQueue.create(RoutingKey.create("route.test"), 101);
    private static InetServer server;

    private static final Logger log = LoggerFactory.getLogger(InetServerImplTest.class);

    @BeforeAll
    static void setUp() {
        server = InetServer.open("localhost", 7777)
                .group(new ChannelPrimaryIOEventLoopGroup(), new ChannelSecondaryIOEventLoopGroup())
                .invokeChannelHandler(ctx -> ctx.chain().add(new TestChannelInboundFilter()))
                .start();

        server.listen().addListener(future -> {
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
        assertTrue(server.isListening());
    }

    @Test
    @Timeout(10)
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

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(rq.size()).isEqualTo(100));

        es.shutdown();
        es.close();
    }

    private static class TestChannelInboundFilter implements ChannelInBoundFilter {

        @Override
        public void doFilter(Context context, ChannelInboundFilterChain chain) throws Exception {
            Buffer buffer = Buffer.alloc(1024);

            int recv = context.recv(buffer);
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
                    context.close();
                } catch (IOException e) {
                    log.info("Failed to close socket = {}", e.getMessage());
                    return;
                } finally {
                    buffer.release();
                }
            }

            chain.doFilter(context);
        }
    }
}