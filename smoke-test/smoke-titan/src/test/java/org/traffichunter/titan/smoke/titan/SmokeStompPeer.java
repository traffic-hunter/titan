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
