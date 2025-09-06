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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLSession;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.Subscription;
import org.traffichunter.titan.core.util.IdGenerator;

/**
 * @author yungwang-o
 */
@Slf4j
public class StompServerConnectionImpl implements StompServerConnection {

    private static final int MAX_SUBSCRIBER = 100;

    private final StompServer server;
    private final SocketChannel socket;
    private final String sessionId = IdGenerator.uuid();

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>(MAX_SUBSCRIBER);

    private volatile Instant lastActivatedTime;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private long pingTimer = -1;
    private long pongTimer = -1;

    public StompServerConnectionImpl(final StompServer server, final SocketChannel socket) {
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
            log.error("Error writing frame = {}", e.getMessage());
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
    public Set<String> ids() {
        return subscriptions.keySet();
    }

    @Override
    public void subscribe(final String id, final Subscription subscription) {
        subscriptions.put(id, subscription);
    }

    @Override
    public void unsubscribe(final String id) {
        subscriptions.remove(id);
    }

    @Override
    public List<Subscription> subscriptions() {
        return subscriptions.values().stream().toList();
    }

    @Override
    public StompServer server() {
        return server;
    }

    @Override
    @CanIgnoreReturnValue
    public Instant setLastActivatedAt() {
        return lastActivatedTime = Instant.now();
    }

    @Override
    public synchronized void setHeartbeat(final long ping, final long pong, final Runnable handler) {
        if (ping > 0) {
            pingTimer = server.setInterval(ping, handler);
        }
        if (pong > 0) {
            pongTimer = server.setInterval(pong, () -> {
                long d = Duration.between(lastActivatedTime, Instant.now()).toMillis();
                if(d > pong * 2) {
                    log.warn("Connection closed due to heartbeat timeout. = {} ms", d);
                    close();
                }
            });
        }
    }

    @Override
    public void close() {
        if(closed.compareAndSet(false, true)) {
            try {
                cancelHeartbeat();
                socket.close();
            } catch (IOException e) {
                log.error("Error closing socket = {}", e.getMessage());
            }
        }
    }

    private void cancelHeartbeat() {
        if (pingTimer >= 0) {
            server.cancelInterval(pingTimer);
            pingTimer = -1;
        }
        if (pongTimer >= 0) {
            server.cancelInterval(pongTimer);
            pongTimer = -1;
        }
    }
}
