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
