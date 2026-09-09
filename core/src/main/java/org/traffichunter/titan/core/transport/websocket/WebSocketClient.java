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
package org.traffichunter.titan.core.transport.websocket;

import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrame;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameException;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameHeader;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.concurrent.ScheduledPromise;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.transport.ClientException;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.channel.ChannelRegistry;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author yun
 */
public final class WebSocketClient {

    private final InetClient inetClient;
    private final Protocol subProtocol;
    private final String path;
    private final ChannelRegistry<WebSocketChannel> channels = new ChannelRegistry<>();

    public WebSocketClient(InetClient inetClient, Protocol subProtocol) {
        this(inetClient, subProtocol, WebSocketPaths.ROOT);
    }

    public WebSocketClient(InetClient inetClient, Protocol subProtocol, String path) {
        this.inetClient = inetClient;
        this.subProtocol = subProtocol;
        this.path = WebSocketPaths.normalize(path);
    }

    public void start() {
        inetClient.start();
    }

    public Promise<WebSocketChannel> connect(String host, int port) {
        return connect(host, port, 30, TimeUnit.SECONDS);
    }

    public Promise<WebSocketChannel> connect(String host, int port, long timeOut, TimeUnit timeUnit) {
        return connect(new InetSocketAddress(host, port), timeOut, timeUnit);
    }

    public Promise<WebSocketChannel> connect(InetSocketAddress remoteAddress) {
        return connect(remoteAddress, 30, TimeUnit.SECONDS);
    }

    public Promise<WebSocketChannel> connect(InetSocketAddress remoteAddress, long timeOut, TimeUnit timeUnit) {
        WebSocketClientHandshaker handshaker = new WebSocketClientHandshaker(remoteAddress.getHostString(), subProtocol, path);
        return inetClient.connect(remoteAddress, timeOut, timeUnit)
                .thenCompose(channel -> {
                    Promise<NetChannel> handshake = handshaker.handshake(channel);
                    ScheduledPromise<?> connectionCheck = channel.eventLoop().scheduleAtFixedRate(() -> {
                        if (!handshake.isDone() && channel.isClosed()) {
                            handshake.tryFail(new WebSocketHandshakeException(
                                    "Connection closed before WebSocket upgrade completed"
                            ));
                        }
                    }, 1, 10, TimeUnit.MILLISECONDS);
                    ScheduledPromise<?> timeout = channel.eventLoop().schedule(() -> {
                        if (!handshake.isDone()) {
                            handshake.tryFail(new WebSocketHandshakeException("WebSocket upgrade timed out"));
                            channel.close();
                        }
                    }, timeOut, timeUnit);
                    handshake.addListener(result -> {
                        connectionCheck.cancel();
                        timeout.cancel();
                    });
                    handshake.onFailure(error -> inetClient.disconnect(channel));
                    return handshake;
                })
                .map(channel -> {
                    WebSocketChannel webSocketChannel = new WebSocketChannel(channel, subProtocol);
                    channels.addChannel(webSocketChannel);
                    return webSocketChannel;
                });
    }

    public Promise<Void> send(WebSocketFrame frame) {
        WebSocketFrameHeader header = frame.header();
        if (!WebSocketFrame.isDataFrame(header.getOpCode())) {
            return inetClient.failedPromise(
                    new WebSocketFrameException("Only data frames can be sent through the payload encoder")
            );
        }

        if (!header.isFin()) {
            return inetClient.failedPromise(
                    new WebSocketFrameException("Fragmented frames are not supported")
            );
        }

        if (frame.subProtocol() != subProtocol) {
            return inetClient.failedPromise(
                    new WebSocketFrameException("Frame subprotocol does not match client subprotocol")
            );
        }

        return send(frame.payload());
    }

    public Promise<Void> send(Buffer buffer) {
        WebSocketChannel channel = readyChannel();
        if (channel == null) {
            return inetClient.failedPromise(new ClientException("WebSocket client is not connected"));
        }
        return inetClient.send(channel.unwrap(), buffer);
    }

    public void disconnect(WebSocketChannel channel) {
        channels.removeChannel(channel);
        inetClient.disconnect(channel.unwrap());
    }

    public void shutdown() {
        channels.getChannels().forEach(this::disconnect);
        inetClient.shutdown();
    }

    public void shutdown(long timeout, TimeUnit unit) {
        channels.getChannels().forEach(this::disconnect);
        inetClient.shutdown(timeout, unit);
    }

    public boolean isStarted() {
        return inetClient.isStarted();
    }

    public boolean isShutdown() {
        return inetClient.isShutdown();
    }

    public Protocol subProtocol() {
        return subProtocol;
    }

    public List<WebSocketChannel> channels() {
        return channels.getChannels();
    }

    private @Nullable WebSocketChannel readyChannel() {
        for (WebSocketChannel channel : channels.getChannels()) {
            if (channel.isConnected() && !channel.isClosed()) {
                return channel;
            }
            channels.removeChannel(channel);
        }
        return null;
    }
}
