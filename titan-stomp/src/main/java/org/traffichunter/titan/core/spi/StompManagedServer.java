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
package org.traffichunter.titan.core.spi;

import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.transport.stomp.StompServer;

import java.util.concurrent.TimeUnit;

/**
 * Managed lifecycle adapter for a STOMP server.
 *
 * <p>The SPI exposes this wrapper to bootstrap code while keeping STOMP-specific access
 * available through {@link #server()} for integrations that need it.</p>
 *
 * @author yun
 */
public final class StompManagedServer implements ManagedServer {

    private final StompServer server;
    private final ServerSettings settings;

    public StompManagedServer(StompServer server, ServerSettings settings) {
        this.server = server;
        this.settings = settings;
    }

    @Override
    public String name() {
        return settings.serverName();
    }

    @Override
    public void start() {
        try {
            server.start();
            server.listen(settings.host(), settings.port()).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start STOMP server " + name(), e);
        }
    }

    public StompServer server() {
        return server;
    }

    @Override
    public void stop() {
        server.shutdown();
    }
}
