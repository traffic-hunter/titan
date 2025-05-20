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
package org.traffichunter.titan.core.transport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import lombok.AccessLevel;
import lombok.Getter;
import org.traffichunter.titan.core.util.Protocol;

/**
 * @author yungwang-o
 */
public abstract class AbstractOutBoundChannel<FRAME> implements Channel {

    @Getter(value = AccessLevel.PROTECTED)
    protected final ClientNIOConnector connector;

    protected final ByteBuffer byteBuffer;

    @Getter
    private final Protocol protocol;

    public AbstractOutBoundChannel(final ClientNIOConnector connector,
                                   final ByteBuffer byteBuffer,
                                   final Protocol protocol) {

        this.connector = connector;
        this.byteBuffer = byteBuffer;
        this.protocol = protocol;
    }

    @Override
    public boolean isOpen() {
        return connector.isOpen();
    }

    public abstract void send(FRAME frame);

    protected abstract boolean validate(FRAME frame);

    @Override
    public void close() throws IOException {
        connector.close();
    }
}
