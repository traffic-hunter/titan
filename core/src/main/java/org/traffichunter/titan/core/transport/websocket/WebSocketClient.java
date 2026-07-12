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
package org.traffichunter.titan.core.transport.websocket;

import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.channel.websocket.WebSocketChannel;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrame;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameException;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameHeader;
import org.traffichunter.titan.core.concurrent.Promise;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.transport.ClientException;
import org.traffichunter.titan.core.transport.InetClient;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.channel.ChannelRegistry;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author yun
 */
public final class WebSocketClient {

    private final InetClient inetClient;
    private final Protocol subProtocol;
    private final ChannelRegistry<WebSocketChannel> channels = new ChannelRegistry<>();

    public WebSocketClient(InetClient inetClient, Protocol subProtocol) {
        this.inetClient = inetClient;
        this.subProtocol = subProtocol;
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
        WebSocketClientHandshaker handshaker = new WebSocketClientHandshaker(remoteAddress.getHostString(), subProtocol);
        return inetClient.connect(remoteAddress, timeOut, timeUnit)
                .thenCompose(channel -> {
                    Promise<NetChannel> handshake = handshaker.handshake(channel);
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
