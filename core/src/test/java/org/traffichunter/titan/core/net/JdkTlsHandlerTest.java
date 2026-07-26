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
package org.traffichunter.titan.core.net;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandlerChainImpl;
import org.traffichunter.titan.core.channel.ChannelSecondaryIOEventLoop;
import org.traffichunter.titan.core.channel.IOEventLoop;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.WorkerEventLoopGroup;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class JdkTlsHandlerTest {

    @Test
    void reject_non_positive_tls_handshake_timeout() {
        JdkTlsHandler handler = handler(mock(SSLEngine.class));
        NetChannel channel = mock(NetChannel.class);

        assertThatThrownBy(() -> handler.handshake(channel, 0, TimeUnit.SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void return_same_result_when_handshake_is_requested_more_than_once() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("tls-repeated-handshake-test");
        SSLEngine sslEngine = mock(SSLEngine.class);
        NetChannel channel = mock(NetChannel.class);

        when(sslEngine.getHandshakeStatus()).thenReturn(SSLEngineResult.HandshakeStatus.NEED_UNWRAP);
        when(channel.eventLoop()).thenReturn(eventLoop);

        eventLoop.start();
        try {
            JdkTlsHandler handler = handler(sslEngine);
            ChannelPromise first = handler.handshake(channel, 1, TimeUnit.SECONDS);
            ChannelPromise second = handler.handshake(channel, 1, TimeUnit.SECONDS);

            eventLoop.submit(() -> {}).get(2, TimeUnit.SECONDS);

            assertThat(second).isSameAs(first);
            verify(sslEngine, times(1)).beginHandshake();

            first.cancel();
        } finally {
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void run_delegated_tasks_on_channel_event_loop_by_default() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("tls-delegated-task-test");
        SSLEngine sslEngine = mock(SSLEngine.class);
        NetChannel channel = mock(NetChannel.class);
        AtomicReference<String> taskThread = new AtomicReference<>();
        Runnable delegatedTask = () -> taskThread.set(Thread.currentThread().getName());

        when(channel.eventLoop()).thenReturn(eventLoop);
        when(sslEngine.getHandshakeStatus())
                .thenReturn(
                        SSLEngineResult.HandshakeStatus.NEED_TASK,
                        SSLEngineResult.HandshakeStatus.FINISHED
                );
        when(sslEngine.getDelegatedTask()).thenReturn(delegatedTask, (Runnable) null);

        eventLoop.start();
        try {
            ChannelPromise handshake = new JdkTlsHandler(sslEngine).handshake(channel);

            handshake.await(2, TimeUnit.SECONDS);

            assertThat(handshake.isSuccess()).isTrue();
            assertThat(taskThread.get()).isEqualTo("tls-delegated-task-test");
        } finally {
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void offload_delegated_tasks_to_explicit_worker_executor() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("tls-delegated-task-test");
        WorkerEventLoopGroup workerGroup = new WorkerEventLoopGroup(1);
        SSLEngine sslEngine = mock(SSLEngine.class);
        NetChannel channel = mock(NetChannel.class);
        AtomicReference<String> taskThread = new AtomicReference<>();
        Runnable delegatedTask = () -> taskThread.set(Thread.currentThread().getName());

        when(channel.eventLoop()).thenReturn(eventLoop);
        when(sslEngine.getHandshakeStatus())
                .thenReturn(
                        SSLEngineResult.HandshakeStatus.NEED_TASK,
                        SSLEngineResult.HandshakeStatus.FINISHED
                );
        when(sslEngine.getDelegatedTask()).thenReturn(delegatedTask, (Runnable) null);

        eventLoop.start();
        workerGroup.start();
        try {
            ChannelPromise handshake = new JdkTlsHandler(
                    sslEngine,
                    new TlsTaskEventLoopExecutor(workerGroup)
            ).handshake(channel);

            handshake.await(2, TimeUnit.SECONDS);

            assertThat(handshake.isSuccess()).isTrue();
            assertThat(taskThread.get()).startsWith("WorkerEventLoopThread-");
        } finally {
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
            workerGroup.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void fail_handshake_when_ssl_engine_cannot_start() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("tls-start-failure-test");
        SSLEngine sslEngine = mock(SSLEngine.class);
        NetChannel channel = mock(NetChannel.class);
        SSLException failure = new SSLException("cannot start");

        when(channel.eventLoop()).thenReturn(eventLoop);
        doThrow(failure).when(sslEngine).beginHandshake();

        eventLoop.start();
        try {
            ChannelPromise handshake = handler(sslEngine).handshake(channel);

            handshake.await(2, TimeUnit.SECONDS);

            assertThat(handshake.isFailed()).isTrue();
            assertThat(handshake.error())
                    .isInstanceOf(NetSecureException.class)
                    .hasCause(failure);
        } finally {
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void reject_application_write_when_tls_outbound_is_closed() throws Exception {
        SSLEngine sslEngine = mock(SSLEngine.class);
        SSLSession session = mock(SSLSession.class);
        NetChannel channel = mock(NetChannel.class);
        IOEventLoop eventLoop = mock(IOEventLoop.class);
        ChannelOutBoundHandlerChainImpl chain = mock(ChannelOutBoundHandlerChainImpl.class);

        when(sslEngine.getSession()).thenReturn(session);
        when(session.getPacketBufferSize()).thenReturn(64);
        when(sslEngine.wrap(any(ByteBuffer.class), any(ByteBuffer.class)))
                .thenReturn(result(SSLEngineResult.Status.CLOSED, 0, 0));
        when(channel.eventLoop()).thenReturn(eventLoop);

        JdkTlsHandler handler = handler(sslEngine);
        handler.handshakeResult = ChannelPromise.newPromise(eventLoop, channel).success();
        Buffer plainText = Buffer.alloc("message");

        handler.sparkChannelWrite(channel, plainText, chain);

        verify(chain).sparkExceptionCaught(any(NetSecureException.class));
        verify(channel).close();
        assertThat(plainText.byteBuf().refCnt()).isZero();
    }

    @Test
    void send_close_notify_when_peer_closes_tls_inbound() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("tls-handler-test");
        SSLEngine sslEngine = mock(SSLEngine.class);
        SSLSession session = mock(SSLSession.class);
        NetChannel channel = mock(NetChannel.class);
        NetChannel.Internal internal = mock(NetChannel.Internal.class);
        AtomicBoolean outboundDone = new AtomicBoolean();

        when(sslEngine.getSession()).thenReturn(session);
        when(session.getApplicationBufferSize()).thenReturn(64);
        when(session.getPacketBufferSize()).thenReturn(64);
        when(sslEngine.isOutboundDone()).thenAnswer(ignored -> outboundDone.get());
        when(sslEngine.unwrap(any(ByteBuffer.class), any(ByteBuffer.class)))
                .thenReturn(result(SSLEngineResult.Status.CLOSED, 1, 0));
        when(sslEngine.wrap(any(ByteBuffer.class), any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer destination = invocation.getArgument(1);
            destination.put((byte) 0x15);
            outboundDone.set(true);
            return result(SSLEngineResult.Status.CLOSED, 0, 1);
        });
        when(channel.eventLoop()).thenReturn(eventLoop);
        when(channel.internal()).thenReturn(internal);

        eventLoop.start();
        Buffer encrypted = Buffer.alloc(new byte[]{0x01});
        Buffer closeNotify = null;
        try {
            JdkTlsHandler handler = handler(sslEngine);

            assertThat(handler.decode(channel, encrypted)).isNull();

            ChannelPromise closeResult = handler.close(channel);
            closeResult.await(2, TimeUnit.SECONDS);

            assertThat(closeResult.isSuccess()).isTrue();
            verify(sslEngine).closeOutbound();

            ArgumentCaptor<Buffer> closeNotifyCaptor = ArgumentCaptor.forClass(Buffer.class);
            verify(internal).write(closeNotifyCaptor.capture());
            verify(internal).flush();

            closeNotify = closeNotifyCaptor.getValue();
            assertThat(closeNotify.getBytes()).containsExactly((byte) 0x15);
        } finally {
            encrypted.release();
            if (closeNotify != null) {
                closeNotify.release();
            }
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void fail_and_close_channel_when_tls_handshake_times_out() throws Exception {
        ChannelSecondaryIOEventLoop eventLoop = new ChannelSecondaryIOEventLoop("tls-handshake-timeout-test");
        SSLEngine sslEngine = mock(SSLEngine.class);
        NetChannel channel = mock(NetChannel.class);

        when(sslEngine.getHandshakeStatus()).thenReturn(SSLEngineResult.HandshakeStatus.NEED_UNWRAP);
        when(channel.eventLoop()).thenReturn(eventLoop);

        eventLoop.start();
        try {
            ChannelPromise handshake = handler(sslEngine)
                    .handshake(channel, 10, TimeUnit.MILLISECONDS);

            handshake.await(2, TimeUnit.SECONDS);

            assertThat(handshake.isFailed()).isTrue();
            assertThat(handshake.error())
                    .isInstanceOf(NetSecureException.class)
                    .hasMessageContaining("TLS handshake timeout");
            verify(channel, timeout(1_000)).close();
        } finally {
            eventLoop.gracefullyShutdown(1, TimeUnit.SECONDS);
        }
    }

    private static SSLEngineResult result(
            SSLEngineResult.Status status,
            int consumed,
            int produced
    ) {
        return new SSLEngineResult(
                status,
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                consumed,
                produced
        );
    }

    private static JdkTlsHandler handler(SSLEngine sslEngine) {
        return new JdkTlsHandler(sslEngine);
    }
}
