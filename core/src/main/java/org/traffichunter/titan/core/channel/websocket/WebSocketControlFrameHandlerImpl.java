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
package org.traffichunter.titan.core.channel.websocket;

import org.traffichunter.titan.core.codec.websocket.WebSocketFrame;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameException;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrames;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * @author yun
 */
public final class WebSocketControlFrameHandlerImpl implements WebSocketControlFrameHandler {

    @Override
    public void handle(WebSocketContext context) {
        WebSocketFrame frame = context.frame();

        switch (frame.header().getOpCode()) {
            case PING -> handlePing(context);
            case PONG -> frame.payload().release();
            case CLOSE -> handleClose(context);
            default -> throw new WebSocketFrameException("Not a control frame");
        }
    }

    private void handlePing(WebSocketContext context) {
        WebSocketFrame frame = context.frame();
        try {
            WebSocketFrame pong = WebSocketFrames.pong(
                    Buffer.heap().alloc(frame.payload().getBytes()),
                    context.side(),
                    frame.subProtocol()
            );
            write(context.channel(), pong);
        } finally {
            frame.payload().release();
        }
    }

    private void handleClose(WebSocketContext context) {
        WebSocketFrame frame = context.frame();
        Buffer closePayload = Buffer.heap().alloc(frame.payload().getBytes());
        try {
            WebSocketFrame close = WebSocketFrames.close(
                    closePayload,
                    context.side(),
                    frame.subProtocol()
            );
            write(context.channel(), close).addListener(result -> context.channel().close());
        } catch (RuntimeException e) {
            closePayload.release();
            throw e;
        } finally {
            frame.payload().release();
        }
    }

    private Promise<Void> write(WebSocketChannel channel, WebSocketFrame frame) {
        return channel.eventLoop().submit(() -> {
            try {
                channel.writeAndFlush(frame);
            } finally {
                frame.payload().release();
            }
        });
    }
}
