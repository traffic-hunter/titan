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
package org.traffichunter.titan.smoke.titan;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A bounded, text-only STOMP peer for malformed input and stopped-reader scenarios.
 *
 * @author yun
 */
final class SmokeStompPeer implements AutoCloseable {

    private final Socket socket = new Socket();

    SmokeStompPeer(int port) throws IOException {
        try {
            socket.setReceiveBufferSize(1024);
            socket.setSoTimeout(10_000);
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress("127.0.0.1", port), 10_000);
            send("CONNECT\naccept-version:1.2\nhost:127.0.0.1\nheart-beat:0,0\n\n\0");
            String connected = readFrame();
            if (!connected.startsWith("CONNECTED\n")) {
                throw new IOException("Expected CONNECTED, received: " + connected);
            }
        } catch (IOException error) {
            try {
                socket.close();
            } catch (IOException cleanup) {
                error.addSuppressed(cleanup);
            }
            throw error;
        }
    }

    void send(String bytes) throws IOException {
        socket.getOutputStream().write(bytes.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    String readFrame() throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (frame.size() < 64 * 1024 && System.nanoTime() < deadline) {
            int value = socket.getInputStream().read();
            if (value == -1) {
                throw new EOFException("Connection closed before a complete STOMP frame");
            }
            if (value == 0) {
                return frame.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
            }
            if (frame.size() == 0 && (value == '\n' || value == '\r')) {
                continue;
            }
            frame.write(value);
        }
        throw new IOException("STOMP response exceeded the smoke peer byte or time limit");
    }

    boolean awaitEof() throws IOException {
        int value;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (System.nanoTime() >= deadline) {
                throw new SocketTimeoutException("Connection did not close after ERROR");
            }
            value = socket.getInputStream().read();
        } while (value == '\n' || value == '\r');
        return value == -1;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
