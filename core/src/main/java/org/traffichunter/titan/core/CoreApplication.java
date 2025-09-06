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
package org.traffichunter.titan.core;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.traffichunter.titan.bootstrap.TitanBootstrap.ApplicationStarter;
import org.traffichunter.titan.bootstrap.Settings;
import org.traffichunter.titan.bootstrap.httpserver.SettingsHttpServer;
import org.traffichunter.titan.core.httpserver.HealthCheckServlet;
import org.traffichunter.titan.core.httpserver.HttpServer;
import org.traffichunter.titan.core.httpserver.jetty.EmbeddedJettyHttpServer;
import org.traffichunter.titan.core.httpserver.jetty.EmbeddedJettyHttpServer.ContextServlet;
import org.traffichunter.titan.monitor.Monitor;
import org.traffichunter.titan.monitor.jmx.heap.HeapData;

/**
 * invoke reflection to bootstrap
 * @author yungwang-o
 */
@SuppressWarnings("unused")
public class CoreApplication implements ApplicationStarter {

    public CoreApplication() {}

    @Override
    public void start(final Settings settings) {

        HttpServer httpServer = initializeHttpServer(settings.settingsHttpServer());
        httpServer.start();

        Monitor monitor = new Monitor(settings.settingsMonitor());
    }

    private EmbeddedJettyHttpServer initializeHttpServer(final SettingsHttpServer settingsHttpServer) {
        return EmbeddedJettyHttpServer.builder()
                .port(settingsHttpServer.port())
                .contextHandler(ServletContextHandler.SESSIONS)
                .threadPool(settingsHttpServer.pooling())
                .addContextServlet(new ContextServlet(HealthCheckServlet.class, "/health-check"))
                .build();
    }

    private static class ApplicationRunner implements Runnable {

        private final Monitor monitor;

        ApplicationRunner(final Monitor monitor) {
            this.monitor = monitor;
        }

        @Override
        public void run() {
            monitor.doMonitor(HeapData.class);
        }
    }

    public static class CoreApplicationException extends RuntimeException {

        public CoreApplicationException() {
        }

        public CoreApplicationException(final String message) {
            super(message);
        }

        public CoreApplicationException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public CoreApplicationException(final Throwable cause) {
            super(cause);
        }
    }
}
