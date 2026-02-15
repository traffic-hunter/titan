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
package org.traffichunter.titan.core.codec.stomp;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.codec.LineFrameChannelDecoder;
import org.traffichunter.titan.core.util.buffer.Buffer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.traffichunter.titan.core.codec.stomp.StompHeaders.*;

/**
 * @author yun
 */
@Slf4j
public class StompChannelDecoder extends ChannelDecoder {

    private static final int DEFAULT_MAX_LENGTH = 65536;

    private final StompParser stompParser;

    public StompChannelDecoder() {
        this(DEFAULT_MAX_LENGTH);
    }

    public StompChannelDecoder(int maxLength) {
        this.stompParser = new StompParser(maxLength);
    }

    @Override
    protected @Nullable Buffer decode(Buffer buffer) {
        StompFrame frame = stompParser.parse(buffer);
        if (frame == null) {
            return null;
        }
        return frame.toBuffer();
    }

    private static class StompParser {

        private static final String CARRIAGE_RETURN = StompDelimiter.CR.getString();
        private static final String LINE_FEED = StompDelimiter.LF.getString();
        private static final String NULL = StompDelimiter.NUL.getString();
        private static final String COLON = StompDelimiter.COLON.getString();
        private static final String COMMA = StompDelimiter.COMMA.getString();

        private static final String CONTENT_LENGTH = "content-length";

        private final LineFrameChannelDecoderWrapper lineFrameDecoder;

        private StompParser(int maxLength) {
            this.lineFrameDecoder = new LineFrameChannelDecoderWrapper(maxLength);
        }

        private @Nullable StompFrame parse(Buffer buffer) {
            final int eol = findEol(buffer);
            if(eol == -1) {
                return null;
            }

            int readerIndex = buffer.byteBuf().readerIndex();
            int length = eol - readerIndex;
            if (length < 0) {
                return null;
            }

            Buffer sliceBuffer = buffer.readSlice(length);

            // Skip stomp last delimiter (null)
            buffer.skipBytes(1);

            Buffer stompFrame = Buffer.alloc(sliceBuffer.length() + 1);
            stompFrame.accumulateBuffer(sliceBuffer)
                    .accumulateByte(StompDelimiter.LF.getHex());

            List<Buffer> frames = lineFrameDecoder.decodes(stompFrame);

            StompCommand stompCommand = StompCommand.valueOf(frames.getFirst().toString());

            int bodyLength = -1;
            StompHeaders headers = new StompHeaders(StompVersion.STOMP_1_2);
            for(int i = 1; i < frames.size(); i++) {
                String header = frames.get(i).toString();
                if(header.isBlank()) {
                    break;
                } else {
                    String[] keyValue = header.split(COLON);

                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    if(key.equals(CONTENT_LENGTH)) {
                        bodyLength = Integer.parseInt(value);
                    }

                    headers.put(Elements.convertToElements(key), value);
                }
            }

            String body = frames.getLast().toString();
            if(bodyLength > -1 && bodyLength != body.length()) {
                return StompFrame.ERR_STOMP_FRAME;
            }

            return StompFrame.create(headers, stompCommand, body.getBytes(StandardCharsets.UTF_8));
        }

        private int findEol(Buffer buffer) {
            final int totalLength = buffer.length();
            final int readIdx = buffer.byteBuf().readerIndex();

            int idx = buffer.indexOf(readIdx, readIdx + totalLength, NULL.charAt(0));
            if(idx >= 0) {
                if(idx > 0 && buffer.getByte(idx - 1) == (byte) NULL.charAt(0)) {
                    return idx - 1;
                }
                return idx;
            }

            return idx;
        }
    }

    static class LineFrameChannelDecoderWrapper extends LineFrameChannelDecoder {

        private LineFrameChannelDecoderWrapper(int maxLength) {
            super(maxLength);
        }

        List<Buffer> decodes(Buffer buffer) {
            List<Buffer> buffers = new LinkedList<>();
            while (buffer.isReadable()) {
                Buffer decode = decode(buffer);
                if (decode != null) {
                    buffers.add(decode);
                }
            }

            return buffers;
        }

        @Override
        protected @Nullable Buffer decode(Buffer buffer) {
            return super.decode(buffer);
        }
    }
}
