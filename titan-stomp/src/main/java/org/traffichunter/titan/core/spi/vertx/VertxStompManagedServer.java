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
package org.traffichunter.titan.core.spi.vertx;

import io.vertx.core.Future;
import io.vertx.ext.stomp.StompServer;
import lombok.extern.slf4j.Slf4j;
import org.traffichunter.titan.bootstrap.ServerSettings;
import org.traffichunter.titan.core.spi.ManagedServer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author yun
 */
@Slf4j
public final class VertxStompManagedServer implements ManagedServer {

    private final StompServer server;
    private final ServerSettings settings;

    public VertxStompManagedServer(StompServer server, ServerSettings settings) {
        this.server = server;
        this.settings = settings;
    }

    @Override
    public String name() {
        return settings.serverName();
    }

    public StompServer server() {
        return server;
    }

    @Override
    public void start() {
        try {
            await(server.listen());
            log.info("Started Vert.x STOMP server {} on {}:{}", name(), settings.host(), settings.port());
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Failed to start Vert.x STOMP server " + name(), e);
        }
    }

    @Override
    public void stop() {
        RuntimeException rex = null;

        try {
            if (server.isListening()) {
                await(server.close());
            }
        } catch (RuntimeException e) {
            rex = e;
        }

        try {
            await(server.close());
        } catch (RuntimeException e) {
            if (rex == null) {
                rex = e;
            }
        }

        if (rex != null) {
            throw new IllegalStateException("Failed to stop Vert.x STOMP server " + name(), rex);
        }
    }

    private static void await(Future<?> future) {
        try {
            future.await(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for Vert.x STOMP operation", e);
        }
    }
}
