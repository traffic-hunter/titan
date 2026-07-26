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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.codec.websocket.WebSocketSide;
import org.traffichunter.titan.core.net.HttpRequest;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * @author yun
 */
public final class WebSocketClientHandshaker extends AbstractWebSocketHandshaker {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClientHandshaker.class);

    private static final String ACCEPT_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String CRLF = "\r\n";

    private static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    private static final String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    private static final String HOST = "Host";
    private static final String UPGRADE = "Upgrade";
    private static final String WEBSOCKET = "websocket";
    private static final String CONNECTION = "Connection";
    private static final String VERSION = "13";

    private static final String WEBSOCKET_URI = "/titan";
    private static final String STATUS_SWITCHING_PROTOCOLS = "HTTP/1.1 101";

    private final String host;
    private final String path;

    public WebSocketClientHandshaker(String host, Protocol subProtocol) {
        this(host, subProtocol, WEBSOCKET_URI);
    }

    public WebSocketClientHandshaker(String host, Protocol subProtocol, String path) {
        super(subProtocol.getSubProtocol(), VERSION);
        if (path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException("WebSocket path must start with '/'");
        }
        this.host = host;
        this.path = path;
    }

    @Override
    public Promise<NetChannel> handshake(NetChannel channel) {
        String key = generateKey();
        HttpRequest request = new HttpRequest()
                .uri(path)
                .header(HOST, host)
                .header(UPGRADE, WEBSOCKET)
                .header(CONNECTION, UPGRADE)
                .header(SEC_WEBSOCKET_KEY, key)
                .header(SEC_WEBSOCKET_PROTOCOL, subProtocol())
                .header(SEC_WEBSOCKET_VERSION, version());

        Promise<NetChannel> upgradeResult = Promise.newPromise(channel.eventLoop());
        channel.chain().add(new WebSocketUpgradeHandler(this, key, upgradeResult));

        channel.writeAndFlush(Buffer.alloc(request.toString())).addListener(result -> {
            if (result.isFailed() && !upgradeResult.isDone()) {
                Throwable error = result.error();
                upgradeResult.fail(error == null
                        ? new WebSocketHandshakeException("Failed to write WebSocket upgrade request")
                        : error);
            }
        });

        return upgradeResult;
    }

    void validateResponse(String response, String key) {
        String[] lines = response.split(CRLF);
        if (lines.length == 0 || !lines[0].startsWith(STATUS_SWITCHING_PROTOCOLS)) {
            throw new WebSocketHandshakeException(
                    "Invalid WebSocket upgrade status: " + (lines.length == 0 ? "" : lines[0]));
        }

        String accept = null;
        String selectedSubProtocol = null;
        for (int i = 1; i < lines.length; i++) {
            int delimiter = lines[i].indexOf(':');
            if (delimiter < 0) {
                continue;
            }

            String name = lines[i].substring(0, delimiter).trim();
            if (SEC_WEBSOCKET_ACCEPT.equalsIgnoreCase(name)) {
                accept = lines[i].substring(delimiter + 1).trim();
            } else if (SEC_WEBSOCKET_PROTOCOL.equalsIgnoreCase(name)) {
                selectedSubProtocol = lines[i].substring(delimiter + 1).trim();
            }
        }

        if (!acceptKey(key).equals(accept)) {
            throw new WebSocketHandshakeException("Invalid Sec-WebSocket-Accept header");
        }
        if (!subProtocol().equals(selectedSubProtocol)) {
            throw new WebSocketHandshakeException(
                    "Invalid Sec-WebSocket-Protocol header: " + selectedSubProtocol
            );
        }
    }

    static String acceptKey(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + ACCEPT_MAGIC).getBytes(ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new WebSocketHandshakeException("SHA-1 is not available", e);
        }
    }

    private static String generateKey() {
        return IdGenerator.randomBase64Id16();
    }

    static final class WebSocketUpgradeHandler extends AbstractWebSocketHandshaker.HttpUpgradeHandler {

        private final String key;
        private final WebSocketClientHandshaker handshaker;

        WebSocketUpgradeHandler(
                WebSocketClientHandshaker handshaker,
                String key,
                Promise<NetChannel> upgradeResult
        ) {
            super(upgradeResult);
            this.handshaker = handshaker;
            this.key = key;
        }

        @Override
        protected void handleHead(NetChannel channel, String head) {
            handshaker.validateResponse(head, key);
            installWebSocketCodec(channel, WebSocketSide.CLIENT, handshaker.subProtocol());
        }

        @Override
        protected void onSuccess(NetChannel channel) {
            log.info("WebSocket connection established");
        }
    }
}
