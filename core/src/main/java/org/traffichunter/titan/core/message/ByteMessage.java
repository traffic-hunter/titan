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
package org.traffichunter.titan.core.message;

import java.time.Instant;
import java.util.Arrays;
import lombok.Builder;
import lombok.Getter;
import org.traffichunter.titan.servicediscovery.RoutingKey;

/**
 * @author yungwang-o
 */
@Getter
public class ByteMessage extends AbstractMessage {

    private final byte[] message;

    @Builder
    public ByteMessage(final Priority priority,
                       final RoutingKey routingKey,
                       final String producerId,
                       final byte[] message) {

        super(priority, routingKey, Instant.now(), false, producerId, message.length);
        this.message = message;
    }

    @Override
    public String toString() {
        return "Message{" + "message=" + Arrays.toString(message) + '}';
    }
}
