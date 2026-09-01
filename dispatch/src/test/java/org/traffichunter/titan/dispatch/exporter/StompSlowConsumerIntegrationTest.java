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
package org.traffichunter.titan.dispatch.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.traffichunter.titan.core.channel.EventLoopGroups;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerChannel;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.management.ChannelWriteBufferResource;
import org.traffichunter.titan.core.util.management.ChannelWriteBufferResourceDetector;
import org.traffichunter.titan.dispatch.AggregationResult;
import org.traffichunter.titan.dispatch.SlowConsumerMetrics;

/**
 * Verifies slow-consumer isolation against a real non-blocking socket.
 *
 * @author yun
 */
class StompSlowConsumerIntegrationTest {

    private InetServer server;
    private Socket slowConsumer;
    private Socket healthyConsumer;

    @AfterEach
    void tearDown() throws Exception {
        if (slowConsumer != null) {
            slowConsumer.close();
        }
        if (healthyConsumer != null) {
            healthyConsumer.close();
        }
        if (server != null && !server.isShutdown()) {
            server.shutdown();
        }
    }

    @RepeatedTest(3)
    @Timeout(20)
    void skip_dispatch_when_real_socket_write_buffer_is_under_pressure() throws Exception {
        LinkedBlockingQueue<NetChannel> acceptedChannels = new LinkedBlockingQueue<>();
        server = InetServer.open(EventLoopGroups.singleGroup())
                .childOption(InetClientOption.builder().sendBufferSize(1024).build())
                .onChannel(channel -> acceptedChannels.add((NetChannel) channel));
        server.start();
        server.listen("localhost", 0).get(5, TimeUnit.SECONDS);

        int port = ((InetSocketAddress) server.localAddress()).getPort();
        slowConsumer = new Socket();
        slowConsumer.setReceiveBufferSize(1024);
        slowConsumer.connect(new InetSocketAddress("localhost", port));

        NetChannel slowChannel = acceptedChannels.poll(5, TimeUnit.SECONDS);
        assertThat(slowChannel).isNotNull();

        healthyConsumer = new Socket();
        healthyConsumer.setSoTimeout(5000);
        healthyConsumer.connect(new InetSocketAddress("localhost", port));
        NetChannel healthyChannel = acceptedChannels.poll(5, TimeUnit.SECONDS);
        assertThat(healthyChannel).isNotNull();

        Destination destination = Destination.create("/topic/slow-consumer");
        StompClientChannel slowStompChannel = StompClientChannel.wrap(slowChannel, StompSessionOption.DEFAULT);
        StompClientChannel healthyStompChannel = StompClientChannel.wrap(healthyChannel, StompSessionOption.DEFAULT);
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        subscriptions.register(StompServerSubscription.builder()
                .destination(destination)
                .id("slow-subscription")
                .ackMode(StompFrame.AckMode.AUTO)
                .connection(slowStompChannel)
                .build());
        subscriptions.register(StompServerSubscription.builder()
                .destination(destination)
                .id("healthy-subscription")
                .ackMode(StompFrame.AckMode.AUTO)
                .connection(healthyStompChannel)
                .build());

        StompServerChannel serverChannel = mock(StompServerChannel.class);
        when(serverChannel.subscriptions()).thenReturn(subscriptions);
        SlowConsumerMetrics metrics = new SlowConsumerMetrics();
        StompDispatchExporter exporter = new StompDispatchExporter(serverChannel, metrics);

        byte[] payload = new byte[8 * 1024];
        AggregationResult result = AggregationResult.create(List.of(destination), 0);
        for (int pressureCycle = 0; pressureCycle < 20 && metrics.getSkippedMessages() == 0; pressureCycle++) {
            for (int attempt = 0; attempt < 4096 && slowChannel.isWritable(); attempt++) {
                slowChannel.writeAndFlush(Buffer.direct().alloc(payload)).get(5, TimeUnit.SECONDS);
            }
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !slowChannel.isWritable());
            Buffer message = Buffer.heap().alloc("message");
            try {
                result = exporter.export(destination, message);
            } finally {
                message.release();
            }
        }

        assertThat(slowChannel.isClosed()).isFalse();
        assertThat(healthyChannel.isWritable()).isTrue();
        ChannelWriteBufferResource writeBuffers = new ChannelWriteBufferResourceDetector().detect();
        assertThat(writeBuffers.pendingBytes()).isPositive();
        assertThat(writeBuffers.nonWritableBuffers()).isPositive();

        AggregationResult completed = result;
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(completed::isDone);
        assertThat(result.totalAttempted()).isEqualTo(2);
        assertThat(result.succeeded()).isOne();
        assertThat(result.failed()).isOne();
        assertThat(metrics.getSkippedMessages()).isOne();

        byte[] received = new byte[1024];
        int read = healthyConsumer.getInputStream().read(received);
        assertThat(read).isPositive();
        assertThat(new String(received, 0, read, StandardCharsets.UTF_8))
                .contains("MESSAGE", "destination:/topic/slow-consumer", "message");
    }
}
