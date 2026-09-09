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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.List;

/**
 * Adapts a Vert.x WebSocket data stream to the NetSocket contract consumed by
 * the Vert.x STOMP connection implementation.
 *
 * <p>Ownership remains with the wrapped WebSocket. This adapter translates stream handlers,
 * writes, close state, addresses, and SSL metadata without introducing another network resource.</p>
 *
 * @author yun
 */
final class VertxWebSocketNetSocket implements NetSocket {

    private final WebSocket webSocket;

    VertxWebSocketNetSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    @Override
    public NetSocket exceptionHandler(Handler<Throwable> handler) {
        webSocket.exceptionHandler(handler);
        return this;
    }

    @Override
    public NetSocket handler(Handler<Buffer> handler) {
        webSocket.handler(handler);
        return this;
    }

    @Override
    public NetSocket pause() {
        webSocket.pause();
        return this;
    }

    @Override
    public NetSocket resume() {
        webSocket.resume();
        return this;
    }

    @Override
    public NetSocket fetch(long amount) {
        webSocket.fetch(amount);
        return this;
    }

    @Override
    public NetSocket endHandler(Handler<Void> handler) {
        webSocket.endHandler(handler);
        return this;
    }

    @Override
    public NetSocket setWriteQueueMaxSize(int maxSize) {
        webSocket.setWriteQueueMaxSize(maxSize);
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        return webSocket.writeQueueFull();
    }

    @Override
    public NetSocket drainHandler(Handler<Void> handler) {
        webSocket.drainHandler(handler);
        return this;
    }

    @Override
    public Future<Void> write(Buffer data) {
        return webSocket.writeBinaryMessage(data);
    }

    @Override
    public String writeHandlerID() {
        return webSocket.binaryHandlerID();
    }

    @Override
    public Future<Void> write(String data) {
        return write(Buffer.buffer(data));
    }

    @Override
    public Future<Void> write(String data, String encoding) {
        return write(Buffer.buffer(data, encoding));
    }

    @Override
    public Future<Void> sendFile(String filename, long offset, long length) {
        return Future.failedFuture(new UnsupportedOperationException("WebSocket transport does not support sendFile"));
    }

    @Override
    public SocketAddress remoteAddress() {
        return webSocket.remoteAddress();
    }

    @Override
    public SocketAddress remoteAddress(boolean real) {
        return webSocket.remoteAddress();
    }

    @Override
    public SocketAddress localAddress() {
        return webSocket.localAddress();
    }

    @Override
    public SocketAddress localAddress(boolean real) {
        return webSocket.localAddress();
    }

    @Override
    public Future<Void> end() {
        return webSocket.end();
    }

    @Override
    public Future<Void> close() {
        return webSocket.close();
    }

    @Override
    public NetSocket closeHandler(Handler<Void> handler) {
        webSocket.closeHandler(handler);
        return this;
    }

    @Override
    public NetSocket shutdownHandler(Handler<Void> handler) {
        webSocket.shutdownHandler(handler);
        return this;
    }

    @Override
    public Future<Void> upgradeToSsl(SSLOptions options, String serverName, Buffer handshake) {
        return Future.failedFuture(new UnsupportedOperationException("WebSocket SSL is configured during connect"));
    }

    @Override
    public boolean isSsl() {
        return webSocket.isSsl();
    }

    @Override
    public SSLSession sslSession() {
        return webSocket.sslSession();
    }

    @Override
    public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
        return webSocket.peerCertificates();
    }

    @Override
    public @Nullable String indicatedServerName() {
        return null;
    }

    @Override
    public String applicationLayerProtocol() {
        return webSocket.subProtocol();
    }
}
