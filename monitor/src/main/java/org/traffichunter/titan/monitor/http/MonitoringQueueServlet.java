package org.traffichunter.titan.monitor.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.codec.json.Json;
import org.traffichunter.titan.core.httpserver.ContentType;
import org.traffichunter.titan.dispatch.DispatcherQueue;
import org.traffichunter.titan.dispatch.DispatcherQueueDeleteResult;
import org.traffichunter.titan.dispatch.DispatcherQueueManager;
import org.traffichunter.titan.dispatch.DispatcherQueueManagers;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.monitor.MonitoringSnapshotService;
import org.traffichunter.titan.monitor.model.QueueSnapshot;

/**
 * HTTP endpoint for dispatcher queue inspection and management.
 *
 * <p>Read operations follow the monitor authorization policy. Changes require both
 * a configured monitor token and a valid bearer token in the request. Queue deletion
 * is therefore unavailable when a local development server runs without authentication.</p>
 *
 * <p>A queue is named by its {@code group} and {@code destination} together. On a read the
 * {@code group} parameter narrows the listing and leaving it out lists every group; on a
 * change leaving it out means the default group, so a request never reaches another
 * namespace by accident.</p>
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
     *
     * <p>Without a {@code group} parameter every group is listed. With one, only that
     * group's queues are returned; a group that holds no queues is an empty list rather
     * than a missing resource.</p>
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!authorization.permit(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        List<QueueSnapshot> queues = service.snapshot().queues();
        if (request.getParameter("group") != null) {
            String group = group(request, response);
            if (group == null) {
                return;
            }
            queues = queues.stream().filter(queue -> group.equals(queue.group())).toList();
        }
        writeJson(response, HttpServletResponse.SC_OK, queues);
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
        String group = group(request, response);
        if (group == null) {
            return;
        }
        Destination destination = destination(request, response);
        if (destination == null) {
            return;
        }

        String action = request.getParameter("action");
        if (action == null || action.isBlank()) {
            createQueue(manager, group, destination, request, response);
            return;
        }
        applyAction(manager, group, destination, action, response);
    }

    private void createQueue(
            DispatcherQueueManager manager,
            String group,
            Destination destination,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        long maxPendingBytes = maxPendingBytes(request, response);
        if (maxPendingBytes <= 0) {
            return;
        }

        DispatcherQueue queue = manager.createQueue(group, destination, maxPendingBytes);
        writeJson(response, HttpServletResponse.SC_OK, snapshot(queue));
    }

    /**
     * Applies a queue state change and reports whether the queue existed.
     *
     * <p>State changes are idempotent, so repeating an action on a queue that is
     * already in the requested state still reports success.</p>
     */
    private void applyAction(
            DispatcherQueueManager manager,
            String group,
            Destination destination,
            String action,
            HttpServletResponse response
    ) throws IOException {
        boolean found;
        switch (action) {
            case "pause" -> found = manager.pauseQueue(group, destination);
            case "resume" -> found = manager.resumeQueue(group, destination);
            case "purge" -> found = manager.purgeQueue(group, destination);
            default -> {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST, new ErrorResponse("unsupported action " + action));
                return;
            }
        }

        if (!found) {
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, new ErrorResponse("queue not found"));
            return;
        }
        writeJson(response, HttpServletResponse.SC_OK, new ActionResponse(action, group, destination.path()));
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
        String group = group(request, response);
        if (group == null) {
            return;
        }
        Destination destination = destination(request, response);
        if (destination == null) {
            return;
        }

        DispatcherQueueDeleteResult result = manager.deleteQueue(group, destination, force(request));
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

    /**
     * Resolves the group a request targets.
     *
     * <p>A missing or blank parameter means the default group, the same reading the STOMP
     * {@code group} header gets. A malformed name is answered with {@code 400} rather than
     * being replaced by a group the caller did not ask for.</p>
     *
     * @return the resolved group, or {@code null} once an error response has been written
     */
    private @Nullable String group(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return DestinationGroups.normalize(request.getParameter("group"));
        } catch (IllegalArgumentException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, new ErrorResponse(e.getMessage()));
            return null;
        }
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

    private long maxPendingBytes(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String raw = request.getParameter("maxPendingBytes");
        if (raw == null || raw.isBlank()) {
            return DispatcherQueue.DEFAULT_MAX_PENDING_BYTES;
        }
        try {
            long maxPendingBytes = Long.parseLong(raw);
            if (maxPendingBytes > 0) {
                return maxPendingBytes;
            }
        } catch (NumberFormatException ignored) {
        }
        writeJson(response, HttpServletResponse.SC_BAD_REQUEST, new ErrorResponse("maxPendingBytes must be greater than zero"));
        return -1;
    }

    private static boolean force(HttpServletRequest request) {
        return Boolean.parseBoolean(request.getParameter("force"));
    }

    private static QueueSnapshot snapshot(DispatcherQueue queue) {
        return new QueueSnapshot(
                queue.getGroup(),
                queue.getDestination(),
                queue.size(),
                queue.getPendingBytes(),
                queue.getMaxPendingBytes(),
                queue.getResumePendingBytes(),
                queue.isPaused()
        );
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

    private record ActionResponse(String status, String group, String destination) {
    }

    private record ErrorResponse(String error) {
    }
}
