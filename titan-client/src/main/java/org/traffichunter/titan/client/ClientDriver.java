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

import java.util.concurrent.TimeUnit;

/**
 * Common lifecycle contract for a client runtime implementation.
 *
 * <p>A driver owns transport-specific resources and supplies the serial {@link Worker} used by
 * the facade to coordinate mutable client state. Protocol-specific drivers extend this contract
 * with their connection operation while keeping native runtime types out of {@link TitanClient}.</p>
 *
 * <p>Implementations must only close resources they created. A runtime supplied by the caller
 * remains caller-owned unless the concrete driver explicitly documents otherwise.</p>
 *
 * @author yun
 */
public interface ClientDriver {

    /**
     * Returns the stable implementation name exposed by the client facade.
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
     * Returns the serial execution context used to coordinate facade state and callbacks.
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
