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
package org.traffichunter.titan.core.codec.stomp;

import java.nio.charset.StandardCharsets;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

/**
 * @author yungwang-o
 */
public class StompUtils {

    public static StompFrame doParse(final String stomp, final StompHeaders headers) {

        int lastIdx = stomp.lastIndexOf(StompDelimiter.NUL.getCharacter());
        if(lastIdx == -1) {
            return StompFrame.ERR_STOMP_FRAME;
        }

        String frameData = stomp.substring(0, lastIdx);

        String[] splitFrameData = frameData.split("\r\n");

        if(splitFrameData.length == 0) {
            return StompFrame.ERR_STOMP_FRAME;
        }

        StompCommand command = StompCommand.valueOf(splitFrameData[0].toUpperCase());

        for(int i = 1; i < splitFrameData.length; i++) {
            String splitFrameDatum = splitFrameData[i];

            if(splitFrameDatum.isEmpty()) {
                break;
            }

            int idx = splitFrameDatum.indexOf(":");
            String key = splitFrameDatum.substring(0, idx);
            String value = splitFrameDatum.substring(idx + 1);

            headers.put(Elements.valueOf(key.toUpperCase()), value);
        }

        final byte[] body = splitFrameData[splitFrameData.length - 1].getBytes(StandardCharsets.UTF_8);
        return StompFrame.create(headers, command, body);
    }
}
