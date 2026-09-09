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

import java.util.concurrent.TimeUnit;

/**
 * Common lifecycle contract for a client runtime implementation.
 *
 * <p>A driver owns transport-specific resources and supplies a {@link Worker} that processes
 * client state changes serially. Protocol-specific drivers add connection operations without
 * adding native runtime types to {@link TitanClient}.</p>
 *
 * <p>Implementations must only close resources they created. A runtime supplied by the caller
 * remains caller-owned unless the concrete driver explicitly documents otherwise.</p>
 *
 * @author yun
 */
public interface ClientDriver {

    /**
     * Returns the stable implementation name reported by the client.
     *
     * @return implementation name
     */
    String name();

    /**
     * Returns the immutable configuration interpreted by this driver.
     *
     * @return client configuration
     */
    ClientConfiguration clientConfiguration();

    /** Starts the runtime resources required before a connection can be opened. */
    void start();

    /**
     * Returns the worker that processes client state changes and callbacks serially.
     *
     * @return driver-owned worker
     */
    Worker worker();

    /**
     * Closes active connections and resources owned by this driver.
     *
     * @param timeout maximum time to wait for graceful shutdown
     * @param unit unit of {@code timeout}
     */
    void close(long timeout, TimeUnit unit);
}
