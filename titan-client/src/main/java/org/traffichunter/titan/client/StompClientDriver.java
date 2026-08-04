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
package org.traffichunter.titan.client;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service-provider contract for a transport-specific STOMP client runtime.
 *
 * <p>A driver translates common {@link ClientConfiguration} values into its native networking
 * implementation and performs one complete transport and STOMP negotiation for each
 * {@link #connect(InetSocketAddress)} call. It does not retain logical client state or schedule
 * reconnect attempts; those responsibilities belong to {@link DefaultTitanClient}.</p>
 *
 * <p>The driver owns only resources it creates. Implementations that wrap a caller-provided
 * runtime must preserve that runtime when {@link #close(long, TimeUnit)} is invoked.</p>
 *
 * @author yun
 */
public interface StompClientDriver {

    /**
     * Returns the implementation name exposed by the client facade.
     *
     * @return stable driver name such as {@code titan} or {@code vertx}
     */
    String name();

    /** Starts resources required to open connections without connecting to the remote server. */
    void start();

    /**
     * Returns the immutable configuration used by this driver.
     *
     * @return configuration shared with the client facade
     */
    ClientConfiguration clientConfiguration();

    /**
     * Returns the serial execution context owned by this driver.
     *
     * @return worker used for client state and transport callbacks
     */
    Worker worker();

    /**
     * Opens and negotiates one STOMP connection to the supplied host and port.
     *
     * @param host remote server host
     * @param port remote server port
     * @return a future completed after both transport setup and STOMP negotiation succeed
     * @throws ClientException if the connection attempt cannot be started
     */
    default CompletableFuture<StompConnection> connect(String host, int port) throws ClientException {
        return connect(new InetSocketAddress(host, port));
    }

    /**
     * Opens and negotiates one STOMP connection to the supplied address.
     *
     * @param remoteAddress remote server socket address
     * @return a future completed with a transport-neutral view of the physical connection
     * @throws ClientException if the connection attempt cannot be started
     */
    CompletableFuture<StompConnection> connect(InetSocketAddress remoteAddress) throws ClientException;

    /**
     * Closes active connections and runtime resources owned by this driver within the timeout.
     *
     * @param timeout maximum time to wait for resource shutdown
     * @param unit unit of {@code timeout}
     */
    void close(long timeout, TimeUnit unit);
}
