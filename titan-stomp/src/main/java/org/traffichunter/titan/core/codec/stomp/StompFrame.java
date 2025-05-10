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
import java.util.Map.Entry;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;

/**
 * @author yungwang-o
 */
@Getter
@Slf4j
public class StompFrame {

    private final StompHeaders headers;

    private final StompCommand command;

    private final byte[] body;

    public StompFrame(final StompHeaders headers, final StompCommand command) {
        this.headers = headers;
        this.command = command;
        this.body = new byte[] {};
    }

    public StompFrame(final StompHeaders headers, final StompCommand command, final byte[] body) {
        this.headers = headers;
        this.command = command;
        this.body = body;
    }

    public String generate() {
        final StringBuilder sb = new StringBuilder();
        sb.append(command.name());

        // CRLF
        sb.append(StompDelimiter.CR.getCharacter()).append(StompDelimiter.LF.getCharacter());

        Set<Entry<Elements, String>> entries = headers.entrySet();
        for(Entry<Elements, String> entry : entries) {
            sb.append(entry.getKey())
                    .append(StompDelimiter.COLON.getCharacter())
                    .append(entry.getValue())
                    .append(StompDelimiter.CR.getCharacter()).append(StompDelimiter.LF.getCharacter());
        }
        sb.append(StompDelimiter.CR.getCharacter()).append(StompDelimiter.LF.getCharacter());

        if(body != null) {
            sb.append(new String(body, StandardCharsets.UTF_8));
        }

        sb.append(StompDelimiter.NUL.getCharacter());
        sb.append(StompDelimiter.CR.getCharacter()).append(StompDelimiter.LF.getCharacter());

        return sb.toString();
    }
}
