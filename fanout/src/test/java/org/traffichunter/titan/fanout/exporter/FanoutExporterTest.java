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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.NetServerChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientConnection;
import org.traffichunter.titan.core.channel.stomp.StompServerConnection;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscription;
import org.traffichunter.titan.core.codec.stomp.StompSubscriptions;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.transport.InetServer;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.channel.ChannelRegistry;
import org.traffichunter.titan.fanout.CompletableResult;

class FanoutExporterTest {

    private static IOEventLoop immediateEventLoop() {
        IOEventLoop loop = mock(IOEventLoop.class);
        when(loop.inEventLoop(any(Thread.class))).thenReturn(true);
        when(loop.inEventLoop()).thenReturn(true);
        return loop;
    }

    @Test
    void completableResult_completes_when_done_reaches_attempted() throws Exception {
        IOEventLoop loop = immediateEventLoop();
        Promise<CompletableResult> promise = Promise.newPromise(loop);
        CompletableResult result = CompletableResult.pending(
                List.of(Destination.create("/topic/test")),
                2,
                promise
        );

        result.markSuccess();
        assertThat(result.promise().isDone()).isFalse();

        result.markFailure();
        result.promise().await(1, TimeUnit.SECONDS);

        assertThat(result.promise().isDone()).isTrue();
        assertThat(result.done()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void stompFanoutExporter_aggregates_success_and_failure() throws Exception {
        IOEventLoop loop = immediateEventLoop();

        StompServerConnection serverConnection = mock(StompServerConnection.class);
        NetServerChannel serverChannel = mock(NetServerChannel.class);
        when(serverConnection.channel()).thenReturn(serverChannel);
        when(serverChannel.eventLoop()).thenReturn(loop);

        StompSubscriptions<StompServerSubscription> subscriptions = new StompSubscriptions<>();
        when(serverConnection.subscriptions()).thenReturn(subscriptions);

        Destination destination = Destination.create("/topic/orders");

        StompClientConnection successConn = mock(StompClientConnection.class);
        Promise<StompFrame> successPromise = Promise.newPromise(loop);
        successPromise.success(StompFrame.PING);
        when(successConn.send(org.mockito.ArgumentMatchers.any(StompFrame.class))).thenReturn(successPromise);

        StompClientConnection failedConn = mock(StompClientConnection.class);
        Promise<StompFrame> failedPromise = Promise.newPromise(loop);
        failedPromise.fail(new IllegalStateException("send failed"));
        when(failedConn.send(org.mockito.ArgumentMatchers.any(StompFrame.class))).thenReturn(failedPromise);

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
        CompletableResult result = exporter.export(destination, Buffer.alloc("hello".getBytes()));
        result.promise().await(1, TimeUnit.SECONDS);

        assertThat(result.totalAttempted()).isEqualTo(2);
        assertThat(result.done()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void tcpFanoutExporter_returns_completed_result_counts() {
        IOEventLoop loop = immediateEventLoop();

        InetServer inetServer = mock(InetServer.class);
        when(inetServer.isStart()).thenReturn(true);

        NetServerChannel serverChannel = mock(NetServerChannel.class);
        when(inetServer.channel()).thenReturn(serverChannel);
        when(serverChannel.eventLoop()).thenReturn(loop);

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
        doThrow(new RuntimeException("boom")).when(channelFail).writeAndFlush(org.mockito.ArgumentMatchers.any(Buffer.class));
        registry.addChannel(channelFail);

        when(inetServer.connections()).thenReturn(registry.selector());

        TcpFanoutExporter exporter = new TcpFanoutExporter(inetServer);
        CompletableResult result = exporter.export(Destination.create("/topic/a"), Buffer.alloc("p".getBytes()));

        assertThat(result.promise().isDone()).isTrue();
        assertThat(result.totalAttempted()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }
}
