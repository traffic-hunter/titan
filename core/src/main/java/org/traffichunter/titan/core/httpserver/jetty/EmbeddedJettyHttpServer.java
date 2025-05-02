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
package org.traffichunter.titan.core.httpserver.jetty;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import jakarta.servlet.Servlet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.traffichunter.titan.bootstrap.httpserver.Pooling;
import org.traffichunter.titan.core.httpserver.HttpServer;
import org.traffichunter.titan.core.httpserver.threadpool.JettyThreadPool;

/**
 * Embedded jetty web-server
 * @author yungwang-o
 */
@Slf4j
public class EmbeddedJettyHttpServer implements HttpServer {

    private static final String ROOT_PATH = "/titan";

    private static final int DEFAULT_PORT = 7777;

    private final Server server;

    private final ServletContextHandler handler;

    private final ServerConnector connector;

    private final int port;

    private EmbeddedJettyHttpServer(final Builder builder) {
        this.handler = builder.handler;
        this.handler.setContextPath(ROOT_PATH);
        this.port = builder.port;
        registerServlets(this.handler, builder.contextServlets);

        this.server = new Server(builder.threadPool);
        this.connector = new ServerConnector(server);
        this.connector.setPort(port);
        this.server.addConnector(connector);
        this.server.setHandler(handler);
        gracefulShutdown(builder.isGracefulShutdown);

        log.info("Embedded Jetty HTTP server ver. {}", server.getServerInfo());
        log.info("Embedded Jetty HTTP server started on port. {}", this.port);
        log.info("Embedded Jetty HTTP server started on servlet context path. {}", this.handler.getContextPath());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public void start(){
        try {
            server.start();
            server.join();
        } catch (Exception e) {
            log.error("Failed to start embedded Jetty HTTP server = {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            server.stop();
        } catch (Exception e) {
            log.error("Failed to stop embedded Jetty HTTP server = {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void registerServlets(final ServletContextHandler servletContextHandler,
                                  final List<ContextServlet> contextServlets) {

        contextServlets.forEach(contextServlet ->
                servletContextHandler.addServlet(contextServlet.servletClass(), contextServlet.pathSpec()));
    }

    private void gracefulShutdown(final boolean isEnable) {
        server.setStopTimeout(5000);
        server.setStopAtShutdown(isEnable);
    }

    @SuppressWarnings("unused")
    public static final class Builder {

        private ThreadPool threadPool;

        private int port = DEFAULT_PORT;

        private ServletContextHandler handler;

        private final List<ContextServlet> contextServlets = new ArrayList<>();

        private boolean isGracefulShutdown = false;

        @CanIgnoreReturnValue
        public Builder threadPool(final JettyThreadPool threadPool) {
            this.threadPool = threadPool.getThreadPool();
            return this;
        }

        @CanIgnoreReturnValue
        public Builder threadPool(final Pooling pooling) {

            this.threadPool = Arrays.stream(JettyThreadPool.values())
                    .map(jettyThreadPool -> jettyThreadPool.match(pooling))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No thread pool found"))
                    .getThreadPool();

            return this;
        }

        @CanIgnoreReturnValue
        public Builder threadPool(final JettyThreadPool threadPool, final int threadPoolSize) {
            this.threadPool = threadPool.getThreadPool(threadPoolSize);
            return this;
        }

        @CanIgnoreReturnValue
        public Builder port(final int port) {
            this.port = port;
            return this;
        }

        // Options is (ServletContextHandler.SESSIONS == 1)
        @CanIgnoreReturnValue
        public Builder contextHandler(final int options) {
            this.handler = new ServletContextHandler(options);
            return this;
        }

        @CanIgnoreReturnValue
        public Builder gracefulShutdown(final boolean isGracefulShutdown) {
            this.isGracefulShutdown = isGracefulShutdown;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder addContextServlet(final ContextServlet contextServlet) {
            this.contextServlets.add(contextServlet);
            return this;
        }

        public EmbeddedJettyHttpServer build() {
            return new EmbeddedJettyHttpServer(this);
        }
    }

    public record ContextServlet(Class<? extends Servlet> servletClass, String pathSpec) { }
}
