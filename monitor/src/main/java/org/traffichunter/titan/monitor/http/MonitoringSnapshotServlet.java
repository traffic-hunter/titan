package org.traffichunter.titan.monitor.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.traffichunter.titan.core.codec.json.Json;
import org.traffichunter.titan.core.httpserver.ContentType;
import org.traffichunter.titan.monitor.MonitoringSnapshotService;

public final class MonitoringSnapshotServlet extends HttpServlet {

    private final MonitoringSnapshotService service;
    private final MonitoringAuthorization authorization;

    public MonitoringSnapshotServlet(MonitoringSnapshotService service, MonitoringAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!authorization.allows(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String json = Json.serialize(service.snapshot());
        if (json == null) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(ContentType.APPLICATION_JSON);
        response.getWriter().write(json);
    }
}
