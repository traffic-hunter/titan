package org.traffichunter.titan.core.transport;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.message.ByteMessage;
import org.traffichunter.titan.core.message.Priority;
import org.traffichunter.titan.core.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.RoutingKey;

/**
 * @author yungwang-o
 */
class InetServerImplTest {

    private final DispatcherQueue rq = DispatcherQueue.create(RoutingKey.create("route.test"), 100);
    private InetServer server;

    private final Logger log = LoggerFactory.getLogger(InetServerImplTest.class);

    @BeforeEach
    void setUp() throws Exception {
        server = InetServer.open(new InetSocketAddress("localhost", 7777));

        server.listen()
                .get()
                .onRead(handle -> {
                    log.info("Handler called with: = {}", new String(handle));
                    ByteMessage msg = ByteMessage.builder()
                            .routingKey(RoutingKey.create("route.test"))
                            .priority(Priority.DEFAULT)
                            .message(handle)
                            .producerId(IdGenerator.uuid())
                            .build();

                    rq.enqueue(msg);
                }).onWrite("hello"::getBytes);

        server.start();
        Thread.sleep(2000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void inet_server_launch_test() {
        assertTrue(server.isListening());
    }

    @Test
    void inet_server_concurrent_connection_test() throws Exception {

        ExecutorService es = Executors.newFixedThreadPool(3);

        CountDownLatch latch = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            es.submit(() -> {
                try (Socket socket = new Socket("localhost", 7777)) {
                    socket.getOutputStream().write("hello".getBytes());
                    socket.getOutputStream().flush();
                    latch.countDown();
                    Thread.sleep(100); // 여유 있게
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        latch.await();

        Thread.sleep(2000);

        System.out.println("RQ SIZE = " + rq.size());
        assertThat(rq.size()).isEqualTo(3);

        es.shutdown();
        es.close();
    }
}