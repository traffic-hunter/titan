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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameDecoder;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameEncoder;
import org.traffichunter.titan.core.codec.websocket.WebSocketSide;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.Protocol;
import org.traffichunter.titan.core.util.buffer.Buffer;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Common WebSocket handshake support for both client and server upgrade flows.
 *
 * @author yun
 */
abstract class AbstractWebSocketHandshaker {

    private final String subProtocol;
    private final String version;

    protected AbstractWebSocketHandshaker(String subProtocol, String version) {
        this.subProtocol = subProtocol;
        this.version = version;
    }

    protected String subProtocol() {
        return subProtocol;
    }

    protected String version() {
        return version;
    }

    public abstract Promise<NetChannel> handshake(NetChannel channel);

    abstract static class HttpUpgradeHandler extends ChannelDecoder {

        private static final Logger log = LoggerFactory.getLogger(HttpUpgradeHandler.class);
        private static final byte LF = '\n';
        private static final byte CR = '\r';

        private static final int MAX_HEAD_LENGTH = 8192;

        private final Promise<NetChannel> upgradeResult;
        private boolean upgraded;
        private boolean failed;

        HttpUpgradeHandler(Promise<NetChannel> upgradeResult) {
            this.upgradeResult = upgradeResult;
        }

        /**
         * Reads the HTTP head and fails the upgrade promise on an invalid or oversized head.
         * The owner of the promise closes the channel.
         */
        @Override
        protected @Nullable Buffer decode(NetChannel channel, Buffer buffer) {
            if (failed) {
                discard(buffer);
                return null;
            }
            if (upgraded) {
                return buffer.isReadable() ? buffer.readRetainedSlice(buffer.length()) : null;
            }

            try {
                int headEnd = findEndOfHead(buffer);
                int headLength = headEnd < 0 ? buffer.length() : headEnd - buffer.byteBuf().readerIndex();
                if (headLength > MAX_HEAD_LENGTH) {
                    throw new WebSocketHandshakeException(
                            WebSocketHandshakeException.REQUEST_HEADER_FIELDS_TOO_LARGE,
                            "HTTP upgrade head exceeds " + MAX_HEAD_LENGTH + " bytes"
                    );
                }
                if (headEnd < 0) {
                    return null;
                }

                Buffer head = buffer.readRetainedSlice(headLength);
                try {
                    handleHead(channel, head.toString(ISO_8859_1));
                } finally {
                    head.release();
                }
                upgraded = true;
                upgradeResult.success(channel);
                onSuccess(channel);
                return buffer.isReadable() ? buffer.readRetainedSlice(buffer.length()) : null;
            } catch (Exception e) {
                failed = true;
                discard(buffer);
                log.warn("Failed to upgrade WebSocket connection. reason={}", e.getMessage());
                upgradeResult.fail(e);
                return null;
            }
        }

        protected abstract void handleHead(NetChannel channel, String head);

        protected void onSuccess(NetChannel channel) {
        }

        protected final void installWebSocketCodec(
                NetChannel channel,
                WebSocketSide side,
                String subProtocol
        ) {
            channel.chain()
                    .addFirst(new WebSocketFrameEncoder(side, subProtocol))
                    .addLast(new WebSocketFrameDecoder(side, Protocol.subProtocol(subProtocol)));
        }

        private static void discard(Buffer buffer) {
            if (buffer.isReadable()) {
                buffer.skipBytes(buffer.length());
            }
        }

        private static int findEndOfHead(Buffer buffer) {
            int cursor = buffer.byteBuf().readerIndex();
            int end = cursor + buffer.length();
            while (cursor < end) {
                int eol = findEol(buffer, cursor, end);
                if (eol < 0) {
                    return -1;
                }

                if (buffer.getByte(eol) != CR) {
                    cursor = eol + 1;
                    continue;
                }
                if (end - eol < 4) {
                    return -1;
                }
                if (buffer.getByte(eol + 2) == CR && buffer.getByte(eol + 3) == LF) {
                    return eol + 4;
                }
                cursor = eol + 2;
            }

            return -1;
        }

        private static int findEol(Buffer buffer, int fromIndex, int toIndex) {
            int idx = buffer.indexOf(fromIndex, toIndex, LF);
            if (idx > fromIndex && buffer.getByte(idx - 1) == CR) {
                return idx - 1;
            }

            return idx;
        }
    }
}
