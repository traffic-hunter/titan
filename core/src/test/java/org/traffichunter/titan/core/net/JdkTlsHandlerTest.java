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
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.util.buffer.Buffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class JdkTlsHandlerTest {

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

        JdkTlsHandler handler = new JdkTlsHandler(sslEngine);
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
            JdkTlsHandler handler = new JdkTlsHandler(sslEngine);

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
}
