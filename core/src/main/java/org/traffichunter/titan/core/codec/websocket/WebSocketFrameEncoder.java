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
