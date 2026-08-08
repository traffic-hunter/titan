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
package org.traffichunter.titan.client;

import io.vertx.ext.stomp.Frame;
import io.vertx.ext.stomp.StompClientConnection;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.channel.stomp.StompClientHandler;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author yun
 */
class BufferOwnershipTest {

    @Test
    void disconnected_client_consumes_payload() {
        TitanClient client = TitanClient.builder().build();
        Buffer payload = Buffer.heap().alloc("message");

        try {
            assertThatThrownBy(() -> client.send("/queue/test", payload).join())
                    .hasCauseInstanceOf(ClientException.class);
            assertThat(payload.byteBuf().refCnt()).isZero();
        } finally {
            client.shutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void disconnected_client_consumes_payload_with_headers() {
        TitanClient client = TitanClient.builder().build();
        Buffer payload = Buffer.heap().alloc("message");

        try {
            assertThatThrownBy(() -> client.send(
                    "/queue/test",
                    payload,
                    Map.of(Elements.RECEIPT, "send-1")
            ).join()).hasCauseInstanceOf(ClientException.class);
            assertThat(payload.byteBuf().refCnt()).isZero();
        } finally {
            client.shutdown(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void native_connection_consumes_payload_when_destination_is_invalid() {
        StompClientChannel channel = mock(StompClientChannel.class);
        when(channel.handler()).thenReturn(mock(StompClientHandler.class));
        TitanStompConnection connection = new TitanStompConnection(channel);
        Buffer payload = Buffer.heap().alloc("message");

        assertThatThrownBy(() -> connection.send("/queue/invalid destination", payload))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(payload.byteBuf().refCnt()).isZero();
    }

    @Test
    void vertx_connection_copies_and_consumes_payload() {
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        Frame response = mock(Frame.class);
        when(nativeConnection.send(eq("/queue/test"), any(io.vertx.core.buffer.Buffer.class)))
                .thenReturn(io.vertx.core.Future.succeededFuture(response));
        VertxStompConnection connection = new VertxStompConnection(nativeConnection);
        Buffer payload = Buffer.heap().alloc("message");

        connection.send("/queue/test", payload).join();

        assertThat(payload.byteBuf().refCnt()).isZero();
        verify(nativeConnection).send(eq("/queue/test"), any(io.vertx.core.buffer.Buffer.class));
    }

    @Test
    void vertx_connection_copies_and_consumes_payload_with_headers() {
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        Frame response = mock(Frame.class);
        when(nativeConnection.send(
                eq("/queue/test"),
                anyMap(),
                any(io.vertx.core.buffer.Buffer.class)
        )).thenReturn(io.vertx.core.Future.succeededFuture(response));
        VertxStompConnection connection = new VertxStompConnection(nativeConnection);
        Buffer payload = Buffer.heap().alloc("message");

        connection.send(
                "/queue/test",
                payload,
                Map.of(Elements.RECEIPT, "send-1")
        ).join();

        assertThat(payload.byteBuf().refCnt()).isZero();
        verify(nativeConnection).send(
                eq("/queue/test"),
                anyMap(),
                any(io.vertx.core.buffer.Buffer.class)
        );
    }

    @Test
    void vertx_connection_consumes_payload_when_destination_is_invalid() {
        StompClientConnection nativeConnection = mock(StompClientConnection.class);
        VertxStompConnection connection = new VertxStompConnection(nativeConnection);
        Buffer payload = Buffer.heap().alloc("message");

        assertThatThrownBy(() -> connection.send("/queue/invalid destination", payload))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(payload.byteBuf().refCnt()).isZero();
    }
}
