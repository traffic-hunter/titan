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
public interface StompClientDriver extends ClientDriver {

    /**
     * Returns the implementation name exposed by the client facade.
     *
     * @return stable driver name such as {@code titan} or {@code vertx}
     */
    @Override
    String name();

    /** Starts resources required to open connections without connecting to the remote server. */
    @Override
    void start();

    /**
     * Returns the immutable configuration used by this driver.
     *
     * @return configuration shared with the client facade
     */
    @Override
    ClientConfiguration clientConfiguration();

    /**
     * Returns the serial execution context owned by this driver.
     *
     * @return worker used for client state and transport callbacks
     */
    @Override
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
    @Override
    void close(long timeout, TimeUnit unit);
}
