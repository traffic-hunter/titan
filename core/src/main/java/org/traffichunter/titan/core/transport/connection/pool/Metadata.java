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
package org.traffichunter.titan.core.transport.connection.pool;

import lombok.Builder;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author yun
 */
@Builder
public record Metadata(
    int maxConnections,
    int minConnections,
    boolean fair,
    InetSocketAddress localAddress,
    InetSocketAddress remoteAddress
) {

    public Metadata {
        if (maxConnections > 100) {
            throw new IllegalArgumentException("maxConnections cannot be greater than 100");
        }
        if (minConnections < 0 || minConnections > maxConnections) {
            throw new IllegalArgumentException("minConnections cannot be negative");
        }
    }

    public static Metadata getDefault(SocketAddress localAddress, SocketAddress remoteAddress) {
        return Metadata.builder()
                .maxConnections(Runtime.getRuntime().availableProcessors() * 2)
                .minConnections(Runtime.getRuntime().availableProcessors())
                .fair(false)
                .localAddress((InetSocketAddress) localAddress)
                .remoteAddress((InetSocketAddress) remoteAddress)
                .build();
    }
}
