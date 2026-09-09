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
package org.traffichunter.titan.core.channel.stomp;

import static org.traffichunter.titan.core.codec.stomp.StompFrame.errorFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public final class StompFrameDispatchHandler implements ChannelInBoundHandler {

    private static final Logger log = LoggerFactory.getLogger(StompFrameDispatchHandler.class);

    private final StompClientChannel stompChannel;
    private final StompHandler stompHandler;

    public StompFrameDispatchHandler(StompClientChannel stompChannel, StompHandler stompHandler) {
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
