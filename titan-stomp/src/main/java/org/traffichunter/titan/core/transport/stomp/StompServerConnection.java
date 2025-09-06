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

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLSession;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.Subscription;
import org.traffichunter.titan.core.util.inet.ServerConnection;

/**
 * @author yungwang-o
 */
public interface StompServerConnection extends ServerConnection {

    void write(StompFrame frame);

    void write(ByteBuffer buf);

    String session();

    SSLSession sslSession();

    Set<String> ids();

    void subscribe(String id, Subscription subscription);

    void unsubscribe(String id);

    /**
     * read-only
     */
    List<Subscription> subscriptions();

    StompServer server();

    Instant setLastActivatedAt();

    void setHeartbeat(long ping, long pong, Runnable handler);

    void close();
}
