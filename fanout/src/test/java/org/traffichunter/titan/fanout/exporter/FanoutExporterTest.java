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
package org.traffichunter.titan.fanout.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.ext.stomp.Command;
import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompServer;
import io.vertx.ext.stomp.StompServerHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.NetServerChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.channel.stomp.StompServerConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.channel.ChannelRegistry;
import org.traffichunter.titan.fanout.AggregationResult;

@ExtendWith(MockitoExtension.class)
class FanoutExporterTest {

    @Mock
    private StompServerConnection serverConnection;

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
    void aggregationResult_completes_when_done_reaches_attempted() {
        AggregationResult result = AggregationResult.create(
                List.of(Destination.create("/topic/test")),
                2
        );

        result.success();
        assertThat(result.isDone()).isFalse();

        result.fail();

        assertThat(result.isDone()).isTrue();
        assertThat(result.done()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void stompFanoutExporter_aggregates_success_and_failure() throws Exception {
        IOEventLoop loop = immediateEventLoop();

        StompServerSubscriptions subscriptions = new StompServerSubscriptions();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);

        Destination destination = Destination.create("/topic/orders");

        StompClientConnection successConn = mock(StompClientConnection.class);
        when(successConn.session()).thenReturn("session-1");
        Promise<StompFrame> successPromise = Promise.newPromise(loop);
        successPromise.success(StompFrame.PING);
        when(successConn.send(any(StompFrame.class))).thenReturn(successPromise);

        StompClientConnection failedConn = mock(StompClientConnection.class);
        when(failedConn.session()).thenReturn("session-2");
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

        StompFanoutExporter exporter = new StompFanoutExporter(serverConnection);
        AggregationResult result = exporter.export(destination, Buffer.alloc("hello".getBytes()));

        assertThat(result.totalAttempted()).isEqualTo(2);
        assertThat(result.done()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void vertxStompFanoutExporter_dispatches_message_frame_to_subscribers() {
        Destination destination = Destination.create("/topic/orders");
        Buffer payload = Buffer.alloc("hello".getBytes());

        when(vertxServer.isListening()).thenReturn(true);
        when(vertxServer.stompHandler()).thenReturn(vertxServerHandler);
        when(vertxServerHandler.getDestination(destination.path())).thenReturn(vertxDestination);
        when(vertxDestination.numberOfSubscriptions()).thenReturn(1);

        VertxStompFanoutExporter exporter = new VertxStompFanoutExporter(vertxServer);
        AggregationResult result = exporter.export(destination, payload);

        ArgumentCaptor<Frame> frameCaptor = ArgumentCaptor.forClass(Frame.class);
        verify(vertxDestination).dispatch(isNull(), frameCaptor.capture());
        Frame frame = frameCaptor.getValue();

        assertThat(frame.getCommand()).isEqualTo(Command.MESSAGE);
        assertThat(frame.getDestination()).isEqualTo(destination.path());
        assertThat(frame.getHeader(Frame.DESTINATION)).isEqualTo(destination.path());
        assertThat(frame.getHeader(Frame.MESSAGE_ID)).isNotBlank();
        assertThat(frame.getHeader(Frame.CONTENT_LENGTH)).isEqualTo(Integer.toString(payload.length()));
        assertThat(frame.getBodyAsString()).isEqualTo("hello");
        assertThat(result.totalAttempted()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void vertxStompFanoutExporter_completes_without_dispatch_when_destination_is_missing() {
        Destination destination = Destination.create("/topic/missing");

        when(vertxServer.isListening()).thenReturn(true);
        when(vertxServer.stompHandler()).thenReturn(vertxServerHandler);
        when(vertxServerHandler.getDestination(destination.path())).thenReturn(null);

        VertxStompFanoutExporter exporter = new VertxStompFanoutExporter(vertxServer);
        AggregationResult result = exporter.export(destination, Buffer.alloc("hello".getBytes()));

        assertThat(result.totalAttempted()).isZero();
        assertThat(result.succeeded()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void tcpFanoutExporter_returns_completed_result_counts() {
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

        TcpFanoutExporter exporter = new TcpFanoutExporter(inetServer);
        AggregationResult result = exporter.export(Destination.create("/topic/a"), Buffer.alloc("p".getBytes()));

        assertThat(result.isDone()).isTrue();
        assertThat(result.totalAttempted()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    private static IOEventLoop immediateEventLoop() {
        IOEventLoop loop = mock(IOEventLoop.class);
        lenient().when(loop.inEventLoop(any(Thread.class))).thenReturn(true);
        lenient().when(loop.inEventLoop()).thenReturn(true);
        return loop;
    }
}
