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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.codec.websocket.WebSocketSide;
import org.traffichunter.titan.core.net.HttpRequest;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Performs the server side of the WebSocket HTTP upgrade handshake.
 *
 * @author yun
 */
public final class WebSocketServerHandshaker extends AbstractWebSocketHandshaker {

    private static final Logger log = LoggerFactory.getLogger(WebSocketServerHandshaker.class);

    private static final String CRLF = "\r\n";
    private static final String METHOD_GET = "GET";
    private static final String SWITCHING_PROTOCOLS_RESPONSE = "HTTP/1.1 101 Switching Protocols";

    private static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    private static final String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    private static final String UPGRADE = "Upgrade";
    private static final String WEBSOCKET = "websocket";
    private static final String CONNECTION = "Connection";
    private static final String UPGRADE_CONNECTION = "Upgrade";
    private static final String VERSION = "13";
    private static final String STOMP_SUB_PROTOCOL = "v12.stomp";
    private final String path;

    public WebSocketServerHandshaker() {
        this(WebSocketPaths.ROOT);
    }

    public WebSocketServerHandshaker(String path) {
        super(STOMP_SUB_PROTOCOL, VERSION);
        this.path = WebSocketPaths.normalize(path);
    }

    @Override
    public Promise<NetChannel> handshake(NetChannel channel) {
        Promise<NetChannel> upgradeResult = Promise.newPromise(channel.eventLoop());
        try {
            channel.chain().add(new WebSocketUpgradeHandler(this, upgradeResult));
        } catch (RuntimeException error) {
            upgradeResult.fail(error);
        }
        return upgradeResult;
    }

    HttpRequest parseRequest(String request) {
        HttpRequest upgradeRequest;
        try {
            upgradeRequest = HttpRequest.parse(request);
        } catch (IllegalArgumentException e) {
            throw new WebSocketHandshakeException(e.getMessage(), e);
        }

        validateRequest(upgradeRequest);
        if (!path.equals(upgradeRequest.uri())) {
            throw new WebSocketHandshakeException("Unexpected WebSocket path: " + upgradeRequest.uri());
        }
        return upgradeRequest;
    }

    String createResponse(HttpRequest request) {
        String key = key(request);
        if (key == null || key.isBlank()) {
            throw new WebSocketHandshakeException("Missing Sec-WebSocket-Key header");
        }

        StringBuilder response = new StringBuilder()
                .append(SWITCHING_PROTOCOLS_RESPONSE).append(CRLF)
                .append(UPGRADE).append(": ").append(WEBSOCKET).append(CRLF)
                .append(CONNECTION).append(": ").append(UPGRADE_CONNECTION).append(CRLF)
                .append(SEC_WEBSOCKET_ACCEPT).append(": ")
                .append(WebSocketClientHandshaker.acceptKey(key))
                .append(CRLF);

        String protocol = protocol(request);
        if (protocol != null) {
            response.append(SEC_WEBSOCKET_PROTOCOL).append(": ").append(protocol).append(CRLF);
        }

        return response.append(CRLF).toString();
    }

    private void validateRequest(HttpRequest request) {
        if (!METHOD_GET.equals(request.method())) {
            throw new WebSocketHandshakeException("Invalid WebSocket upgrade method: " + request.method());
        }
        if (!WEBSOCKET.equalsIgnoreCase(request.header(UPGRADE))) {
            throw new WebSocketHandshakeException("Invalid WebSocket Upgrade header");
        }
        if (!containsToken(request.header(CONNECTION), UPGRADE_CONNECTION)) {
            throw new WebSocketHandshakeException("Invalid WebSocket Connection header");
        }
        if (!VERSION.equals(request.header(SEC_WEBSOCKET_VERSION))) {
            throw new WebSocketHandshakeException("Invalid WebSocket version");
        }
        String key = key(request);
        if (key == null || key.isBlank()) {
            throw new WebSocketHandshakeException("Missing Sec-WebSocket-Key header");
        }

        String protocol = protocol(request);
        if (protocol != null && !containsToken(protocol, STOMP_SUB_PROTOCOL)) {
            throw new WebSocketHandshakeException("Unsupported WebSocket subprotocol: " + protocol);
        }
    }

    private static @Nullable String key(HttpRequest request) {
        return request.header(SEC_WEBSOCKET_KEY);
    }

    private @Nullable String protocol(HttpRequest request) {
        String protocol = request.header(SEC_WEBSOCKET_PROTOCOL);
        if (protocol == null) {
            return null;
        }
        return containsToken(protocol, STOMP_SUB_PROTOCOL) ? STOMP_SUB_PROTOCOL : protocol;
    }

    private static boolean containsToken(@Nullable String header, String expected) {
        if (header == null) {
            return false;
        }
        String[] tokens = header.split(",");
        for (String token : tokens) {
            if (expected.equalsIgnoreCase(token.trim())) {
                return true;
            }
        }
        return false;
    }

    static final class WebSocketUpgradeHandler extends AbstractWebSocketHandshaker.HttpUpgradeHandler {

        private @Nullable HttpRequest request;
        private final WebSocketServerHandshaker handshaker;

        WebSocketUpgradeHandler(WebSocketServerHandshaker handshaker, Promise<NetChannel> upgradeResult) {
            super(upgradeResult);
            this.handshaker = handshaker;
        }

        @Override
        protected void handleHead(NetChannel channel, String head) {
            HttpRequest parsed = handshaker.parseRequest(head);
            channel.writeAndFlush(Buffer.heap().alloc(handshaker.createResponse(parsed)));
            installWebSocketCodec(channel, WebSocketSide.SERVER, handshaker.subProtocol());
            request = parsed;
        }

        @Override
        protected void onSuccess(NetChannel channel) {
            HttpRequest parsed = request;
            if (parsed != null) {
                log.info("WebSocket server connection established. uri={}", parsed.uri());
            }
        }
    }
}
