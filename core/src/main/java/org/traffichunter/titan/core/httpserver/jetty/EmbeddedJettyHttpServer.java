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
package org.traffichunter.titan.core.httpserver.jetty;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import jakarta.servlet.Servlet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.traffichunter.titan.core.httpserver.HttpServer;
import org.traffichunter.titan.core.httpserver.threadpool.JettyThreadPool;
import org.traffichunter.titan.core.util.Pooling;

/**
 * Embedded jetty web-server
 * @author yungwang-o
 */
public class EmbeddedJettyHttpServer implements HttpServer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedJettyHttpServer.class);

    private static final String ROOT_PATH = "/titan";

    private static final int DEFAULT_PORT = 7777;

    private final Server server;

    private final ServletContextHandler handler;

    private final ServerConnector connector;

    private final int port;

    private final String host;

    private EmbeddedJettyHttpServer(final Builder builder) {
        this.handler = builder.handler;
        this.handler.setContextPath(ROOT_PATH);
        this.port = builder.port;
        this.host = builder.host;
        registerServlets(this.handler, builder.contextServlets);
        registerServletInstances(this.handler, builder.contextServletInstances);

        this.server = new Server(builder.threadPool);
        this.connector = new ServerConnector(server);
        this.connector.setHost(host);
        this.connector.setPort(port);
        this.server.addConnector(connector);
        this.server.setHandler(handler);
        gracefulShutdown(builder.isGracefulShutdown);

        log.info("Embedded Jetty HTTP server ver. {}", server.getServerInfo());
        log.info("Embedded Jetty HTTP server started on address. {}:{}", this.host, this.port);
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

    private void registerServletInstances(final ServletContextHandler servletContextHandler,
                                          final List<ContextServletInstance> contextServlets) {

        contextServlets.forEach(contextServlet ->
                servletContextHandler.addServlet(new ServletHolder(contextServlet.servlet()), contextServlet.pathSpec()));
    }

    private void gracefulShutdown(final boolean isEnable) {
        server.setStopTimeout(5000);
        server.setStopAtShutdown(isEnable);
    }

    @SuppressWarnings("unused")
    public static final class Builder {

        private ThreadPool threadPool;

        private String host = "0.0.0.0";

        private int port = DEFAULT_PORT;

        private ServletContextHandler handler;

        private final List<ContextServlet> contextServlets = new ArrayList<>();

        private final List<ContextServletInstance> contextServletInstances = new ArrayList<>();

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

        @CanIgnoreReturnValue
        public Builder host(final String host) {
            this.host = host;
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

        @CanIgnoreReturnValue
        public Builder addContextServlet(final ContextServletInstance contextServlet) {
            this.contextServletInstances.add(contextServlet);
            return this;
        }

        public EmbeddedJettyHttpServer build() {
            return new EmbeddedJettyHttpServer(this);
        }
    }

    public record ContextServlet(Class<? extends Servlet> servletClass, String pathSpec) { }

    public record ContextServletInstance(Servlet servlet, String pathSpec) { }
}
