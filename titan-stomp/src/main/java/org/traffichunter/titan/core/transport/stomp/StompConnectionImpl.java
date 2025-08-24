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
package org.traffichunter.titan.core.transport.stomp;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLSession;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.util.IdGenerator;

/**
 * @author yungwang-o
 */
@Slf4j
public class StompConnectionImpl implements StompServerConnection {

    private final StompServer server;
    private final SocketChannel socket;
    private final String sessionId = IdGenerator.uuid();

    private volatile Instant lastActivatedTime;

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public StompConnectionImpl(final StompServer server, final SocketChannel socket) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(socket, "socket");
        this.server = server;
        this.socket = socket;
    }

    @Override
    public void write(final StompFrame frame) {
        write(frame.toBuffer());
    }

    @Override
    public void write(final ByteBuffer buf) {
        try {
            socket.write(buf);
        } catch (IOException e) {
            log.error("Error writing frame", e);
        }
    }

    @Override
    public String session() {
        return sessionId;
    }

    @Override
    public SSLSession sslSession() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StompServer server() {
        return server;
    }

    @Override
    @CanIgnoreReturnValue
    public Instant lastActivatedAt() {
        lock.lock();
        try {
            this.lastActivatedTime = Instant.now();
            return lastActivatedTime;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        if(closed.compareAndSet(false, true)) {

            try {
                socket.close();
            } catch (IOException ignore) { }
        }
    }
}
