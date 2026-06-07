package org.traffichunter.titan.monitor.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.traffichunter.titan.core.httpserver.ContentType;

/**
 * @author yun
 */
public final class MonitoringHealthServlet extends HttpServlet {

    private final MonitoringAuthorization authorization;

    public MonitoringHealthServlet(MonitoringAuthorization authorization) {
        this.authorization = authorization;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!authorization.permit(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(ContentType.APPLICATION_JSON);
        response.getWriter().write("{\"status\":\"UP\"}");
    }
}
