/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.channel.stomp;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.errorFrame;

import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.channel.ChannelInBoundHandler;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.stomp.StompDelimiter;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Decoder output(Buffer) -> StompFrame -> StompHandler dispatch.
 *
 * @author yun
 */
@Slf4j
public final class StompFrameDispatchHandler implements ChannelInBoundHandler {

    private final StompNetChannel stompChannel;
    private final StompHandler stompHandler;

    public StompFrameDispatchHandler(StompNetChannel stompChannel, StompHandler stompHandler) {
        this.stompChannel = stompChannel;
        this.stompHandler = stompHandler;
    }

    @Override
    public void sparkChannelRead(NetChannel channel, Buffer buffer, ChannelInBoundHandlerChain chain) {
        try {
            final StompFrame frame = toFrame(buffer);
            if (frame == StompFrame.ERR_STOMP_FRAME) {
                stompChannel.send(errorFrame("Invalid STOMP frame.", "Invalid STOMP frame."));
                stompChannel.close();
                return;
            }

            stompHandler.handle(frame, stompChannel);
        } catch (Exception e) {
            log.error("Failed to dispatch STOMP frame", e);
            stompChannel.send(errorFrame("Failed to dispatch frame.", e.getMessage() == null ? "" : e.getMessage()));
            stompChannel.close();
        } finally {
            buffer.release();
        }
    }

    private StompFrame toFrame(Buffer buffer) {
        String payload = buffer.toString();
        if (payload.equals(StompDelimiter.LF.getString())) {
            return StompFrame.PING;
        }

        return StompFrame.doParse(payload, StompHeaders.create());
    }
}
