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
