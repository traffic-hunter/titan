package org.traffichunter.titan.monitor.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.json.Json;
import org.traffichunter.titan.core.httpserver.ContentType;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueueDeleteResult;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueueManager;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueueManagers;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.monitor.MonitoringSnapshotService;
import org.traffichunter.titan.monitor.model.QueueSnapshot;

/**
 * HTTP endpoint for dispatcher queue inspection and management.
 *
 * <p>Read operations follow the monitor authorization policy. Mutating
 * operations are stricter: they require a configured monitor token and a valid
 * bearer token on the request. This prevents queue deletion from being exposed
 * accidentally when the monitor server is started without authentication for
 * local development.</p>
 *
 * @author yungwang-o
 */
public final class MonitoringQueueServlet extends HttpServlet {

    private final MonitoringSnapshotService service;
    private final MonitoringAuthorization authorization;

    public MonitoringQueueServlet(MonitoringSnapshotService service, MonitoringAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    /**
     * Returns queue snapshots collected from JMX.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!authorization.permit(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        writeJson(response, HttpServletResponse.SC_OK, service.snapshot().queues());
    }

    /**
     * Creates a queue through the registered runtime queue manager.
     *
     * <p>Creation is idempotent. If the destination is already registered, the
     * existing queue is returned.</p>
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!allowsManagement(request, response)) {
            return;
        }

        DispatcherQueueManager manager = manager(request, response);
        if (manager == null) {
            return;
        }
        Destination destination = destination(request, response);
        if (destination == null) {
            return;
        }
        int capacity = capacity(request, response);
        if (capacity <= 0) {
            return;
        }

        DispatcherQueue queue = manager.createQueue(destination, capacity);
        writeJson(response, HttpServletResponse.SC_OK, snapshot(queue));
    }

    /**
     * Deletes a queue through the registered runtime queue manager.
     *
     * <p>Non-empty queues return {@code 409 Conflict} unless the request uses
     * {@code force=true}.</p>
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!allowsManagement(request, response)) {
            return;
        }

        DispatcherQueueManager manager = manager(request, response);
        if (manager == null) {
            return;
        }
        Destination destination = destination(request, response);
        if (destination == null) {
            return;
        }

        DispatcherQueueDeleteResult result = manager.deleteQueue(destination, force(request));
        switch (result.status()) {
            case DELETED -> writeJson(response, HttpServletResponse.SC_OK, new DeleteResponse("deleted", result.size()));
            case NOT_FOUND -> writeJson(response, HttpServletResponse.SC_NOT_FOUND, new ErrorResponse("queue not found"));
            case NOT_EMPTY -> writeJson(response, HttpServletResponse.SC_CONFLICT, new ErrorResponse("queue is not empty"));
        }
    }

    private boolean allowsManagement(HttpServletRequest request, HttpServletResponse response) {
        if (!authorization.required()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        if (!authorization.permit(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }

    private @Nullable DispatcherQueueManager manager(HttpServletRequest request, HttpServletResponse response) {
        String server = request.getParameter("server");
        DispatcherQueueManager manager = server == null || server.isBlank()
                ? DispatcherQueueManagers.getDefault()
                : DispatcherQueueManagers.get(server);
        if (manager == null) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return null;
        }
        return manager;
    }

    private @Nullable Destination destination(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String raw = request.getParameter("destination");
        if (raw == null || raw.isBlank()) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, new ErrorResponse("destination is required"));
            return null;
        }
        try {
            return Destination.create(raw);
        } catch (IllegalArgumentException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, new ErrorResponse(e.getMessage()));
            return null;
        }
    }

    private int capacity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String raw = request.getParameter("capacity");
        if (raw == null || raw.isBlank()) {
            return DispatcherQueue.DEFAULT_CAPACITY;
        }
        try {
            int capacity = Integer.parseInt(raw);
            if (capacity > 0) {
                return capacity;
            }
        } catch (NumberFormatException ignored) {
        }
        writeJson(response, HttpServletResponse.SC_BAD_REQUEST, new ErrorResponse("capacity must be greater than zero"));
        return -1;
    }

    private static boolean force(HttpServletRequest request) {
        return Boolean.parseBoolean(request.getParameter("force"));
    }

    private static QueueSnapshot snapshot(DispatcherQueue queue) {
        return new QueueSnapshot(queue.getDestination(), queue.size(), queue.capacity(), queue.isPaused());
    }

    private static void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        String json = Json.serialize(body);
        if (json == null) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.setStatus(status);
        response.setContentType(ContentType.APPLICATION_JSON);
        response.getWriter().write(json);
    }

    private record DeleteResponse(String status, int size) {
    }

    private record ErrorResponse(String error) {
    }
}
