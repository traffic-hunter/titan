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
package org.traffichunter.titan.dispatch.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompServer;
import io.vertx.ext.stomp.StompServerHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.NetServerChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompServerChannel;
import org.traffichunter.titan.core.codec.stomp.StompCommand;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.channel.ChannelRegistry;
import org.traffichunter.titan.dispatch.SlowConsumerMetrics;

@ExtendWith(MockitoExtension.class)
class DispatchExporterTest {

    @Mock
    private StompServerChannel serverConnection;

    @Mock
    private NetServerChannel serverChannel;

    @Mock
    private InetServer inetServer;

    @Mock
    private StompServer vertxServer;

    @Mock
    private StompServerHandler vertxServerHandler;

    @Mock
    private io.vertx.ext.stomp.Destination vertxDestination;

    @Test
    void default_message_export_releases_temporary_buffer() {
        Message message = Message.builder()
                .destination(Destination.create("/topic/test"))
                .createdAt(Instant.now())
                .producerId("producer")
                .body("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();

        AtomicReference<Buffer> exported = new AtomicReference<>();
        DispatchExporter exporter = new DispatchExporter() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
                assertThat(payload.byteBuf().refCnt()).isOne();
                exported.set(payload);
                return CompletableFuture.completedFuture(null);
            }
        };

        exporter.export(message.getGroup(), message.getDestination(), message);

        assertThat(exported.get().byteBuf().refCnt()).isZero();
    }

    @Test
    void export_stage_completes_once_every_subscriber_has_been_handed_the_frame() {
        IOEventLoop loop = immediateEventLoop();
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);
        Destination destination = Destination.create("/topic/orders");

        StompClientChannel settledConn = writableConnection(loop, "session-1");
        StompClientChannel pendingConn = writableConnection(loop, "session-2");
        // This connection has taken the frame but its socket has not: the export is still done.
        Promise<StompFrame> pendingWrite = Promise.newPromise(loop);
        when(pendingConn.send(any(StompFrame.class))).thenReturn(pendingWrite);

        subscriptions.register(subscription(null, destination, "sub-1", settledConn));
        subscriptions.register(subscription(null, destination, "sub-2", pendingConn));

        StompDispatchExporter exporter = new StompDispatchExporter(serverConnection);
        CompletableFuture<@Nullable Void> completion = exporter
                .export(DestinationGroups.DEFAULT, destination, Buffer.heap().alloc("hello".getBytes()))
                .toCompletableFuture();

        assertThat(completion).isDone();
        verify(settledConn).send(any(StompFrame.class));
        verify(pendingConn).send(any(StompFrame.class));
        assertThat(pendingWrite.isDone()).isFalse();
    }

    @Test
    void export_stage_completes_normally_when_a_write_fails() {
        IOEventLoop loop = immediateEventLoop();
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);
        Destination destination = Destination.create("/topic/orders");

        StompClientChannel failingConn = writableConnection(loop, "session-1");
        Promise<StompFrame> failedWrite = Promise.newPromise(loop);
        failedWrite.fail(new IllegalStateException("send failed"));
        when(failingConn.send(any(StompFrame.class))).thenReturn(failedWrite);
        subscriptions.register(subscription(null, destination, "sub-1", failingConn));

        StompDispatchExporter exporter = new StompDispatchExporter(serverConnection);
        CompletableFuture<@Nullable Void> completion = exporter
                .export(DestinationGroups.DEFAULT, destination, Buffer.heap().alloc("hello".getBytes()))
                .toCompletableFuture();

        // Callers treat completion as the end of the export, so one unreachable subscriber
        // must not turn it into a failure.
        assertThat(completion).isCompleted();
        assertThat(completion).isNotCompletedExceptionally();
    }

    @Test
    void export_stage_completes_when_a_subscriber_rejects_the_send_immediately() {
        IOEventLoop loop = immediateEventLoop();
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);
        Destination destination = Destination.create("/topic/orders");

        StompClientChannel failingConn = writableConnection(loop, "session-1");
        when(failingConn.send(any(StompFrame.class))).thenThrow(new IllegalStateException("send failed"));
        subscriptions.register(subscription(null, destination, "sub-1", failingConn));

        CompletableFuture<@Nullable Void> completion = new StompDispatchExporter(serverConnection)
                .export(DestinationGroups.DEFAULT, destination, Buffer.heap().alloc("hello".getBytes()))
                .toCompletableFuture();

        assertThat(completion).isCompleted();
        assertThat(completion).isNotCompletedExceptionally();
    }

    @Test
    void expired_stomp_export_does_not_send_when_the_event_loop_resumes() {
        IOEventLoop loop = mock(IOEventLoop.class);
        AtomicReference<Runnable> pendingAttempt = new AtomicReference<>();
        doAnswer(call -> {
            pendingAttempt.set(call.getArgument(0));
            return null;
        }).when(loop).execute(any(Runnable.class));

        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);
        Destination destination = Destination.create("/topic/late");
        StompClientChannel connection = writableConnection(loop, "session-1");
        subscriptions.register(subscription(null, destination, "sub-1", connection));

        // A zero timeout expires the attempt before the loop gets to run it.
        StompDispatchExporter exporter = new StompDispatchExporter(
                serverConnection, SlowConsumerMetrics.global(), Duration.ZERO);
        Buffer payload = Buffer.heap().alloc("hello".getBytes());
        try {
            CompletableFuture<@Nullable Void> completion = exporter
                    .export(DestinationGroups.DEFAULT, destination, payload)
                    .toCompletableFuture();

            assertThat(pendingAttempt.get()).isNotNull();
            assertThat(completion).isNotDone();
            pendingAttempt.get().run();

            assertThat(completion).isDone();
            verify(connection, never()).send(any(StompFrame.class));
        } finally {
            payload.release();
        }
    }

    @Test
    void export_stage_completes_when_no_subscription_matches() {
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);

        StompDispatchExporter exporter = new StompDispatchExporter(serverConnection);
        CompletableFuture<@Nullable Void> completion = exporter
                .export(DestinationGroups.DEFAULT, Destination.create("/topic/empty"), Buffer.heap().alloc("x".getBytes()))
                .toCompletableFuture();

        assertThat(completion).isDone();
    }

    @Test
    void default_message_export_holds_the_buffer_until_the_stage_completes() {
        Message message = Message.builder()
                .destination(Destination.create("/topic/test"))
                .createdAt(Instant.now())
                .producerId("producer")
                .body("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();

        AtomicReference<Buffer> exported = new AtomicReference<>();
        CompletableFuture<@Nullable Void> pending = new CompletableFuture<>();
        DispatchExporter exporter = new DispatchExporter() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public CompletionStage<@Nullable Void> export(String group, Destination destination, Buffer payload) {
                exported.set(payload);
                return pending;
            }
        };

        exporter.export(message.getGroup(), message.getDestination(), message);

        // An exporter that is still writing reads this buffer, so export cannot release it on return.
        assertThat(exported.get().byteBuf().refCnt()).isOne();

        pending.complete(null);

        assertThat(exported.get().byteBuf().refCnt()).isZero();
    }

    @Test
    void stompFanoutExporter_writes_to_every_matching_subscriber() throws Exception {
        IOEventLoop loop = immediateEventLoop();

        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);

        Destination destination = Destination.create("/topic/orders");

        StompClientChannel successConn = mock(StompClientChannel.class);
        NetChannel successChannel = mock(NetChannel.class);
        when(successConn.session()).thenReturn("session-1");
        when(successConn.channel()).thenReturn(successChannel);
        when(successChannel.eventLoop()).thenReturn(loop);
        when(successChannel.isWritable()).thenReturn(true);
        Promise<StompFrame> successPromise = Promise.newPromise(loop);
        successPromise.success(StompFrame.PING);
        when(successConn.send(any(StompFrame.class))).thenReturn(successPromise);

        StompClientChannel failedConn = mock(StompClientChannel.class);
        NetChannel failedChannel = mock(NetChannel.class);
        when(failedConn.session()).thenReturn("session-2");
        when(failedConn.channel()).thenReturn(failedChannel);
        when(failedChannel.eventLoop()).thenReturn(loop);
        when(failedChannel.isWritable()).thenReturn(true);
        Promise<StompFrame> failedPromise = Promise.newPromise(loop);
        failedPromise.fail(new IllegalStateException("send failed"));
        when(failedConn.send(any(StompFrame.class))).thenReturn(failedPromise);

        subscriptions.register(StompServerSubscription.builder()
                .destination(destination)
                .id("sub-1")
                .ackMode(StompFrame.AckMode.AUTO)
                .connection(successConn)
                .build());
        subscriptions.register(StompServerSubscription.builder()
                .destination(destination)
                .id("sub-2")
                .ackMode(StompFrame.AckMode.AUTO)
                .connection(failedConn)
                .build());

        StompDispatchExporter exporter = new StompDispatchExporter(serverConnection);
        exporter.export(DestinationGroups.DEFAULT, destination, Buffer.heap().alloc("hello".getBytes()));

        verify(successConn).send(any(StompFrame.class));
        verify(failedConn).send(any(StompFrame.class));
    }

    @Test
    void stompFanoutExporter_skips_non_writable_subscriber() {
        IOEventLoop loop = immediateEventLoop();
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);

        Destination destination = Destination.create("/topic/orders");
        StompClientChannel connection = mock(StompClientChannel.class);
        NetChannel channel = mock(NetChannel.class);
        when(connection.session()).thenReturn("session-1");
        when(connection.channel()).thenReturn(channel);
        when(channel.eventLoop()).thenReturn(loop);
        when(channel.isWritable()).thenReturn(false);

        subscriptions.register(StompServerSubscription.builder()
                .destination(destination)
                .id("sub-1")
                .ackMode(StompFrame.AckMode.AUTO)
                .connection(connection)
                .build());

        SlowConsumerMetrics metrics = new SlowConsumerMetrics();
        StompDispatchExporter exporter = new StompDispatchExporter(serverConnection, metrics);
        CompletionStage<@Nullable Void> completion =
                exporter.export(DestinationGroups.DEFAULT, destination, Buffer.heap().alloc("hello".getBytes()));

        verify(connection, never()).send(any(StompFrame.class));
        assertThat(metrics.getSkippedMessages()).isOne();
        assertThat(completion.toCompletableFuture()).isDone();
    }

    @Test
    void vertxStompDispatchExporter_dispatches_message_frame_to_subscribers() {
        Destination destination = Destination.create("/topic/orders");
        Buffer payload = Buffer.heap().alloc("hello".getBytes());

        when(vertxServer.isListening()).thenReturn(true);
        when(vertxServer.stompHandler()).thenReturn(vertxServerHandler);
        when(vertxServerHandler.getDestination(destination.path())).thenReturn(vertxDestination);

        VertxStompDispatchExporter exporter = new VertxStompDispatchExporter(vertxServer);
        exporter.export(DestinationGroups.DEFAULT, destination, payload);

        ArgumentCaptor<Frame> frameCaptor = ArgumentCaptor.forClass(Frame.class);
        verify(vertxDestination).dispatch(isNull(), frameCaptor.capture());
        Frame frame = frameCaptor.getValue();

        assertThat(frame.getCommand()).isEqualTo(Command.MESSAGE);
        assertThat(frame.getDestination()).isEqualTo(destination.path());
        assertThat(frame.getHeader(Frame.DESTINATION)).isEqualTo(destination.path());
        assertThat(frame.getHeader(Frame.MESSAGE_ID)).isNotBlank();
        assertThat(frame.getHeader(Frame.CONTENT_LENGTH)).isEqualTo(Integer.toString(payload.length()));
        assertThat(frame.getBodyAsString()).isEqualTo("hello");
    }

    @Test
    void vertxStompDispatchExporter_returns_without_dispatch_when_destination_is_missing() {
        Destination destination = Destination.create("/topic/missing");

        when(vertxServer.isListening()).thenReturn(true);
        when(vertxServer.stompHandler()).thenReturn(vertxServerHandler);
        when(vertxServerHandler.getDestination(destination.path())).thenReturn(null);

        VertxStompDispatchExporter exporter = new VertxStompDispatchExporter(vertxServer);
        exporter.export(DestinationGroups.DEFAULT, destination, Buffer.heap().alloc("hello".getBytes()));

        verify(vertxDestination, never()).dispatch(any(), any(Frame.class));
    }

    @Test
    void tcpFanoutExporter_keeps_writing_after_a_channel_fails() {
        when(inetServer.isStarted()).thenReturn(true);

        ChannelRegistry<NetChannel> registry = new ChannelRegistry<>();
        NetChannel channelOk = mock(NetChannel.class);
        when(channelOk.id()).thenReturn("ok");
        when(channelOk.isActive()).thenReturn(true);
        when(channelOk.isClosed()).thenReturn(false);
        registry.addChannel(channelOk);

        NetChannel channelFail = mock(NetChannel.class);
        when(channelFail.id()).thenReturn("fail");
        when(channelFail.isActive()).thenReturn(true);
        when(channelFail.isClosed()).thenReturn(false);
        doThrow(new RuntimeException("boom")).when(channelFail).writeAndFlush(any(Buffer.class));
        registry.addChannel(channelFail);

        when(inetServer.childChannel()).thenReturn(registry.getChannels());

        TcpDispatchExporter exporter = new TcpDispatchExporter(inetServer);
        exporter.export(DestinationGroups.DEFAULT, Destination.create("/topic/a"), Buffer.heap().alloc("p".getBytes()));

        verify(channelOk).writeAndFlush(any(Buffer.class));
        verify(channelFail).writeAndFlush(any(Buffer.class));
    }

    @Test
    void vertxStompDispatchExporter_refuses_a_group_it_cannot_keep_to_itself() {
        when(vertxServer.isListening()).thenReturn(true);

        VertxStompDispatchExporter exporter = new VertxStompDispatchExporter(vertxServer);

        // Vert.x resolves subscribers by path, so delivering here would hand a market message to
        // every subscriber of the destination, default group included.
        assertThatThrownBy(() -> exporter.export(
                "market",
                Destination.create("/topic/orders"),
                Buffer.heap().alloc("hello".getBytes())
        )).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("market");

        verify(vertxServer, never()).stompHandler();
    }

    @Test
    void tcpFanoutExporter_refuses_a_group_it_cannot_keep_to_itself() {
        when(inetServer.isStarted()).thenReturn(true);

        TcpDispatchExporter exporter = new TcpDispatchExporter(inetServer);

        assertThatThrownBy(() -> exporter.export(
                "market",
                Destination.create("/topic/a"),
                Buffer.heap().alloc("p".getBytes())
        )).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("market");

        verify(inetServer, never()).childChannel();
    }

    private static IOEventLoop immediateEventLoop() {
        IOEventLoop loop = mock(IOEventLoop.class);
        lenient().when(loop.inEventLoop(any(Thread.class))).thenReturn(true);
        lenient().when(loop.inEventLoop()).thenReturn(true);
        lenient().doAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return null;
        }).when(loop).execute(any(Runnable.class));
        return loop;
    }

    @Test
    void stomp_exporter_delivers_only_to_matching_group_and_echoes_group_header() {
        IOEventLoop loop = immediateEventLoop();
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);
        Destination destination = Destination.create("/topic/price");

        StompClientChannel defaultConn = writableConnection(loop, "session-default");
        StompClientChannel marketConn = writableConnection(loop, "session-market");
        subscriptions.register(subscription(null, destination, "sub-default", defaultConn));
        subscriptions.register(subscription("market", destination, "sub-market", marketConn));

        StompDispatchExporter exporter = new StompDispatchExporter(serverConnection);
        exporter.export("market", destination, Buffer.heap().alloc("hello".getBytes()));

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(marketConn).send(sent.capture());
        verify(defaultConn, never()).send(any(StompFrame.class));
        assertThat(sent.getValue().getCommand()).isEqualTo(StompCommand.MESSAGE);
        assertThat(sent.getValue().getHeader(Elements.GROUP)).isEqualTo("market");
        assertThat(sent.getValue().getHeader(Elements.SUBSCRIPTION)).isEqualTo("sub-market");
    }

    @Test
    void stomp_exporter_omits_group_header_for_default_group() {
        IOEventLoop loop = immediateEventLoop();
        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);
        Destination destination = Destination.create("/topic/price");

        StompClientChannel defaultConn = writableConnection(loop, "session-default");
        StompClientChannel marketConn = writableConnection(loop, "session-market");
        subscriptions.register(subscription(null, destination, "sub-default", defaultConn));
        subscriptions.register(subscription("market", destination, "sub-market", marketConn));

        StompDispatchExporter exporter = new StompDispatchExporter(serverConnection);
        exporter.export(DestinationGroups.DEFAULT, destination, Buffer.heap().alloc("hello".getBytes()));

        ArgumentCaptor<StompFrame> sent = ArgumentCaptor.forClass(StompFrame.class);
        verify(defaultConn).send(sent.capture());
        verify(marketConn, never()).send(any(StompFrame.class));
        // Clients that never send the header must never receive it, or their decoder rejects the frame.
        assertThat(sent.getValue().getHeader(Elements.GROUP)).isNull();
    }

    private static StompClientChannel writableConnection(IOEventLoop loop, String session) {
        StompClientChannel connection = mock(StompClientChannel.class);
        NetChannel channel = mock(NetChannel.class);
        when(connection.session()).thenReturn(session);
        // The connection in the other group is filtered out before any of these are touched.
        lenient().when(connection.channel()).thenReturn(channel);
        lenient().when(channel.eventLoop()).thenReturn(loop);
        lenient().when(channel.isWritable()).thenReturn(true);
        Promise<StompFrame> promise = Promise.newPromise(loop);
        promise.success(StompFrame.PING);
        lenient().when(connection.send(any(StompFrame.class))).thenReturn(promise);
        return connection;
    }

    private static StompServerSubscription subscription(
            String group,
            Destination destination,
            String id,
            StompClientChannel connection
    ) {
        return StompServerSubscription.builder()
                .group(group)
                .destination(destination)
                .id(id)
                .ackMode(StompFrame.AckMode.AUTO)
                .connection(connection)
                .build();
    }
}
