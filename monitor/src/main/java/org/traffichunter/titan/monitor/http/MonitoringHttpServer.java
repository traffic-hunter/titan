package org.traffichunter.titan.monitor.http;

import jakarta.servlet.Servlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.httpserver.HttpServer;
import org.traffichunter.titan.core.httpserver.jetty.EmbeddedJettyHttpServer;
import org.traffichunter.titan.core.httpserver.threadpool.JettyThreadPool;
import org.traffichunter.titan.monitor.MonitoringSnapshotService;

public final class MonitoringHttpServer implements HttpServer {

    public static final String SNAPSHOT_PATH = "/monitor/snapshot";
    public static final String HEALTH_PATH = "/monitor/health";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 7777;

    private static final Logger log = LoggerFactory.getLogger(MonitoringHttpServer.class);

    private final EmbeddedJettyHttpServer server;

    private MonitoringHttpServer(Builder builder) {
        MonitoringAuthorization authorization = new MonitoringAuthorization(builder.token);
        if (!authorization.required()) {
            log.warn("Titan monitor token is not configured. Bind monitor HTTP server to a trusted interface only.");
        }

        this.server = EmbeddedJettyHttpServer.builder()
                .host(builder.host)
                .port(builder.port)
                .threadPool(JettyThreadPool._QUEUED, builder.threadPoolSize)
                .contextHandler(0)
                .addContextServlet(servlet(new MonitoringSnapshotServlet(builder.service, authorization), SNAPSHOT_PATH))
                .addContextServlet(servlet(new MonitoringHealthServlet(authorization), HEALTH_PATH))
                .gracefulShutdown(true)
                .build();
    }

    public static Builder builder(MonitoringSnapshotService service) {
        return new Builder(service);
    }

    @Override
    public int getPort() {
        return server.getPort();
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.close();
    }

    private static EmbeddedJettyHttpServer.ContextServletInstance servlet(Servlet servlet, String path) {
        return new EmbeddedJettyHttpServer.ContextServletInstance(servlet, path);
    }

    public static final class Builder {

        private final MonitoringSnapshotService service;
        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private int threadPoolSize = 8;
        private String token = "";

        private Builder(MonitoringSnapshotService service) {
            this.service = service;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public MonitoringHttpServer build() {
            return new MonitoringHttpServer(this);
        }
    }
}
