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
package org.traffichunter.titan.core.channel.websocket;

import org.traffichunter.titan.core.codec.websocket.WebSocketFrame;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrameException;
import org.traffichunter.titan.core.codec.websocket.WebSocketFrames;
import org.traffichunter.titan.core.concurrent.Promise;
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
