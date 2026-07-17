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
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameDecoder;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameEncoder;
import org.traffichunter.titan.core.codec.websocket.WebSocketSide;
import org.traffichunter.titan.core.concurrent.Promise;
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

        private final Promise<NetChannel> upgradeResult;
        private boolean upgraded;

        HttpUpgradeHandler(Promise<NetChannel> upgradeResult) {
            this.upgradeResult = upgradeResult;
        }

        @Override
        protected @Nullable Buffer decode(NetChannel channel, Buffer buffer) {
            if (upgraded) {
                return buffer.isReadable() ? buffer.readRetainedSlice(buffer.length()) : null;
            }

            int headEnd = findEndOfHead(buffer);
            if (headEnd < 0) {
                return null;
            }

            int readerIndex = buffer.byteBuf().readerIndex();
            Buffer head = buffer.readRetainedSlice(headEnd - readerIndex);
            try {
                handleHead(channel, head.toString(ISO_8859_1));
                upgraded = true;
                upgradeResult.success(channel);
                onSuccess(channel);
                return buffer.isReadable() ? buffer.readRetainedSlice(buffer.length()) : null;
            } catch (Exception e) {
                upgradeResult.fail(e);
                channel.close();
                log.error("Failed to upgrade WebSocket connection", e);
                return null;
            } finally {
                head.release();
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
                    .add(new WebSocketFrameEncoder(side, subProtocol))
                    .add(new WebSocketFrameDecoder(side, Protocol.subProtocol(subProtocol)));
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
