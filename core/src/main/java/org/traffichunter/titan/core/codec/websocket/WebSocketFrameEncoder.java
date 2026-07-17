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
package org.traffichunter.titan.core.codec.websocket;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.ChannelEncoder;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yun
 */
public class WebSocketFrameEncoder extends ChannelEncoder {

    private static final Logger log = LoggerFactory.getLogger(WebSocketFrameEncoder.class);

    private final WebSocketFrameParser parser;

    public WebSocketFrameEncoder(WebSocketSide side) {
        this(side, Protocol.STOMP);
    }

    public WebSocketFrameEncoder(WebSocketSide side, String subProtocol) {
        this(side, Protocol.subProtocol(subProtocol));
    }

    public WebSocketFrameEncoder(WebSocketSide side, Protocol subProtocol) {
        this.parser = new WebSocketFrameParser(side, subProtocol);
    }

    @Override
    protected @Nullable Buffer encode(NetChannel channel, Buffer buffer) {
        try {
            WebSocketFrame websocketFrame = parser.parse(buffer);
            return websocketFrame.encode();
        } catch (WebSocketFrameException e) {
            log.warn("Rejected invalid websocket frame. reason={}", e.getMessage());
            channel.close();
            return null;
        } catch (Exception e) {
            log.error("Failed to encode websocket frame", e);
            channel.close();
            return null;
        } finally {
            buffer.release();
        }
    }

    private static final class WebSocketFrameParser {

        private final WebSocketSide side;
        private final Protocol subProtocol;

        private WebSocketFrameParser(WebSocketSide side, Protocol subProtocol) {
            this.side = side;
            this.subProtocol = subProtocol;
        }

        WebSocketFrame parse(Buffer payload) {
            WebSocketFrameHeader.Builder webSocketFrameHeaderBuilder = WebSocketFrameHeader.builder()
                    .op(opCode(subProtocol), true)
                    .payloadLength(payload.length());

            if(side == WebSocketSide.CLIENT) {
                webSocketFrameHeaderBuilder.masked(WebSocketFrameHeader.generateMaskingKey());
            }

            WebSocketFrameHeader webSocketFrameHeader = webSocketFrameHeaderBuilder.build();

            validate(side, webSocketFrameHeader);

            return new WebSocketFrame(
                    webSocketFrameHeader,
                    payload,
                    subProtocol
            );
        }

        private static void validate(WebSocketSide side, WebSocketFrameHeader header) {
            switch (side) {
                case SERVER -> {
                    if (header.isMasked()) {
                        throw new WebSocketFrameException("Masked frames are not allowed on the server");
                    }
                }
                case CLIENT -> {
                    if (!header.isMasked()) {
                        throw new WebSocketFrameException("Masked frames are not allowed on the client");
                    }
                }
            }
        }

        private static WebSocketFrameHeader.OpCode opCode(Protocol subProtocol) {
            return switch (subProtocol) {
                case STOMP -> WebSocketFrameHeader.OpCode.TEXT;
                case MQTT -> WebSocketFrameHeader.OpCode.BINARY;
            };
        }
    }
}
