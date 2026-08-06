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
package org.traffichunter.titan.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.resilience.retry.RetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryExecutors;
import org.traffichunter.titan.core.resilience.retry.RetryResult;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Default stateful implementation of the transport-neutral {@link TitanClient} facade.
 *
 * <p>This class owns the logical client lifecycle, delegates each connection attempt to a
 * {@link StompClientDriver}, and exposes the resulting physical connection through one stable
 * public API. It also installs user callbacks on every new connection and starts the configured
 * retry policy after an unexpected close or connection drop.</p>
 *
 * <p>An explicit {@link #disconnect()} changes the lifecycle state before closing the physical
 * connection, so it does not trigger reconnect. {@link #shutdown(long, TimeUnit)} additionally
 * cancels pending reconnect work and releases the runtime owned by the driver.</p>
 *
 * @author yun
 */
public final class DefaultTitanClient implements TitanClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultTitanClient.class);

    private final StompClientDriver driver;
    private final ClientConfiguration configuration;
    private final RetryExecutor reconnectExecutor;
    private final SubscriptionManager subscriptionManager = new SubscriptionManager();
    private final AtomicReference<Status> status = new AtomicReference<>(Status.INITIALIZED);
    private final AtomicReference<@Nullable RetryResult> reconnectResult = new AtomicReference<>();
    private final Worker worker;

    private volatile @Nullable StompConnection connection;
    private volatile Handler<StompFrames> errorHandler = ignored -> {};
    private volatile Handler<TitanClient> closeHandler = ignored -> {};
    private volatile Handler<TitanClient> connectionDroppedHandler = ignored -> {};
    private volatile Handler<TitanClient> pingHandler = ignored -> {};
    private volatile Handler<Throwable> exceptionHandler = ignored -> {};

    /**
     * Creates a client facade for the supplied transport driver.
     *
     * <p>The driver is not started by this constructor. Call {@link #start()} before
     * {@link #connect()}.</p>
     *
     * @param driver driver responsible for physical STOMP connections
     * @param worker serial context used for client state and callbacks
     */
    public DefaultTitanClient(StompClientDriver driver, Worker worker) {
        this.driver = driver;
        this.configuration = driver.clientConfiguration();
        this.reconnectExecutor = RetryExecutors.eventLoopRetryExecutor(
                configuration.reconnectPolicy(),
                configuration.reconnectListener()
        );
        this.worker = worker;
    }

    /**
     * Creates a client facade using the worker supplied by the driver.
     *
     * @param driver driver responsible for runtime, worker, and physical connections
     */
    public DefaultTitanClient(StompClientDriver driver) {
        this(driver, driver.worker());
    }

    @Override
    public String name() {
        return driver.name();
    }

    @Override
    public void start() {
        if (!status.compareAndSet(Status.INITIALIZED, Status.STARTING)) {
            throw new ClientException("Client is already started");
        }

        try {
            driver.start();
            status.set(Status.STARTED);
        } catch (RuntimeException error) {
            status.set(Status.INITIALIZED);
            throw error;
        }
    }

    @Override
    public CompletableFuture<TitanClient> connect() {
        if (!status.compareAndSet(Status.STARTED, Status.CONNECTING)) {
            return CompletableFuture.failedFuture(new ClientException(
                    status.get() == Status.INITIALIZED
                            ? "Client is not started"
                            : "Client is not ready to connect"
            ));
        }

        CompletableFuture<StompConnection> connectionFuture;
        try {
            connectionFuture = driver.connect(configuration.host(), configuration.port());
        } catch (RuntimeException error) {
            status.compareAndSet(Status.CONNECTING, Status.STARTED);
            return CompletableFuture.failedFuture(error);
        }

        return connectionFuture.thenApplyAsync(conn -> {
                    if (status.get() != Status.CONNECTING) {
                        conn.disconnect();
                        throw new ClientException("Client stopped while connecting");
                    }

                    bind(conn);
                    if (!status.compareAndSet(Status.CONNECTING, Status.CONNECTED)) {
                        conn.disconnect();
                        throw new ClientException("Client stopped while connecting");
                    }

                    if (!conn.isConnected()) {
                        handleConnectionLoss();
                    }
                    return (TitanClient) this;
                }, worker)
                .whenCompleteAsync((client, error) -> {
                    if (error != null) {
                        status.compareAndSet(Status.CONNECTING, Status.STARTED);
                    }
                }, worker);
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload) {
        StompConnection connection = activeConnection();
        if (connection == null) {
            return notConnected();
        }

        return connection.send(destination, payload);
    }

    @Override
    public CompletableFuture<StompFrames> send(
            String destination,
            Buffer payload,
            Map<Elements, String> headers
    ) {
        StompConnection connection = activeConnection();
        if (connection == null) {
            return notConnected();
        }

        return connection.send(destination, payload, headers);
    }

    @Override
    public CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler) {
        StompConnection source = activeConnection();
        if (source == null) {
            return notConnected();
        }

        return source.subscribe(destination, handler)
                .thenComposeAsync(subscriptionId -> {
                    StompHeaders stompHeaders = StompHeaders.create();
                    stompHeaders.put(Elements.ID, subscriptionId);
                    subscriptionManager.add(new Subscription(
                            subscriptionId,
                            Destination.create(destination),
                            stompHeaders,
                            handler
                    ));

                    StompConnection current = this.connection;
                    if (current != null && current != source) {
                        return current.subscribe(destination, stompHeaders.toMap(), handler)
                                .thenApply(ignored -> subscriptionId);
                    }
                    return CompletableFuture.completedFuture(subscriptionId);
                }, worker);
    }

    @Override
    public CompletableFuture<String> subscribe(
            String destination,
            Map<Elements, String> headers,
            Handler<StompFrames> handler
    ) {
        StompConnection source = activeConnection();
        if (source == null) {
            return notConnected();
        }

        return source.subscribe(destination, headers, handler)
                .thenComposeAsync(subscriptionId -> {
                    StompHeaders stompHeaders = new StompHeaders(headers);
                    stompHeaders.put(Elements.ID, subscriptionId);
                    subscriptionManager.add(new Subscription(
                            subscriptionId,
                            Destination.create(destination),
                            stompHeaders,
                            handler
                    ));

                    StompConnection current = this.connection;
                    if (current != null && current != source) {
                        return current.subscribe(destination, stompHeaders.toMap(), handler)
                                .thenApply(ignored -> subscriptionId);
                    }
                    return CompletableFuture.completedFuture(subscriptionId);
                }, worker);
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId) {
        StompConnection source = activeConnection();
        if (source == null) {
            return notConnected();
        }

        return source.unsubscribe(subscriptionId)
                .thenComposeAsync(frames -> {
                    subscriptionManager.remove(subscriptionId);
                    StompConnection current = this.connection;
                    if (current != null && current != source) {
                        return current.unsubscribe(subscriptionId).thenApply(ignored -> frames);
                    }
                    return CompletableFuture.completedFuture(frames);
                }, worker);
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers) {
        StompConnection source = activeConnection();
        if (source == null) {
            return notConnected();
        }

        return source.unsubscribe(subscriptionId, headers)
                .thenComposeAsync(frames -> {
                    subscriptionManager.remove(subscriptionId);
                    StompConnection current = this.connection;
                    if (current != null && current != source) {
                        return current.unsubscribe(subscriptionId, headers).thenApply(ignored -> frames);
                    }
                    return CompletableFuture.completedFuture(frames);
                }, worker);
    }

    @Override
    public CompletableFuture<StompFrames> ack(String messageId) {
        StompConnection connection = activeConnection();
        if (connection == null) {
            return notConnected();
        }

        return connection.ack(messageId);
    }

    @Override
    public CompletableFuture<StompFrames> nack(String messageId) {
        StompConnection connection = activeConnection();
        if (connection == null) {
            return notConnected();
        }

        return connection.nack(messageId);
    }

    @Override
    public CompletableFuture<StompFrames> disconnect() {
        StompConnection connection = this.connection;
        if (connection == null || !status.compareAndSet(Status.CONNECTED, Status.STARTED)) {
            return notConnected();
        }

        cancelReconnect();
        return connection.disconnect()
                .whenCompleteAsync((frame, error) -> {
                    if (error != null && connection.isConnected()) {
                        status.compareAndSet(Status.STARTED, Status.CONNECTED);
                        return;
                    }

                    subscriptionManager.clear();
                    if (this.connection == connection) {
                        this.connection = null;
                    }
                }, worker);
    }

    @Override
    public TitanClient errorHandler(Handler<StompFrames> handler) {
        this.errorHandler = handler;
        return this;
    }

    @Override
    public TitanClient closeHandler(Handler<TitanClient> handler) {
        this.closeHandler = handler;
        return this;
    }

    @Override
    public TitanClient connectionDroppedHandler(Handler<TitanClient> handler) {
        this.connectionDroppedHandler = handler;
        return this;
    }

    @Override
    public TitanClient pingHandler(Handler<TitanClient> handler) {
        this.pingHandler = handler;
        return this;
    }

    @Override
    public TitanClient exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public boolean isConnected() {
        StompConnection connection = this.connection;
        return status.get() == Status.CONNECTED && connection != null && connection.isConnected();
    }

    @Override
    public boolean isStarted() {
        return Status.isRunning(status.get());
    }

    @Override
    public boolean isShutdown() {
        return status.get() == Status.SHUTDOWN;
    }

    @Override
    public void shutdown(long timeout, TimeUnit unit) {
        if (!transitionToShuttingDown()) {
            return;
        }

        cancelReconnect();
        RuntimeException failure = null;
        try {
            reconnectExecutor.shutdown(timeout, unit);
        } catch (RuntimeException error) {
            failure = error;
        }
        try {
            driver.close(timeout, unit);
        } catch (RuntimeException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        } finally {
            subscriptionManager.clear();
            connection = null;
            status.set(Status.SHUTDOWN);
        }

        if (failure != null) {
            throw failure;
        }
    }

    ClientConfiguration configuration() {
        return configuration;
    }

    StompClientDriver driver() {
        return driver;
    }

    StompConnection connection() {
        StompConnection connection = this.connection;
        if (connection == null) {
            throw new IllegalStateException("STOMP client is not connected");
        }
        return connection;
    }

    private void bind(StompConnection connection) {
        this.connection = connection;
        connection.errorHandler(frame -> worker.execute(() ->
                notifyHandler(() -> errorHandler.handle(frame))
        ));
        connection.closeHandler(ignored -> worker.execute(() -> {
            if (this.connection == connection) {
                handleConnectionLoss();
                notifyHandler(() -> closeHandler.handle(this));
            }
        }));
        connection.connectionDroppedHandler(ignored -> worker.execute(() -> {
            if (this.connection == connection) {
                handleConnectionLoss();
                notifyHandler(() -> connectionDroppedHandler.handle(this));
            }
        }));
        connection.pingHandler(ignored -> worker.execute(() -> {
            if (this.connection == connection) {
                notifyHandler(() -> pingHandler.handle(this));
            }
        }));
        connection.exceptionHandler(error -> worker.execute(() -> {
            if (this.connection == connection) {
                notifyHandler(() -> exceptionHandler.handle(error));
            }
        }));
    }

    /**
     * Moves an active client back to connecting and starts one retry sequence.
     *
     * <p>Both close and connection-drop callbacks may describe the same transport failure. The
     * status transition ensures only the first callback creates reconnect work.</p>
     */
    private void handleConnectionLoss() {
        if (!status.compareAndSet(Status.CONNECTED, Status.CONNECTING)) {
            return;
        }

        RetryResult result = reconnectExecutor.retry(this::reconnect);

        RetryResult previous = reconnectResult.getAndSet(result);
        if (previous != null) {
            previous.cancel();
        }
        if (status.get() != Status.CONNECTING && reconnectResult.compareAndSet(result, null)) {
            result.cancel();
        }
    }

    /**
     * Performs one reconnect attempt on the retry executor.
     *
     * <p>The replacement connection is bound first, then a snapshot of logical subscriptions is
     * restored within one shared timeout. The client becomes connected only after every restore
     * succeeds and the replacement connection is still active. Throwing keeps the retry sequence
     * alive; a normal return ends the current sequence.</p>
     */
    private void reconnect() {
        if (status.get() != Status.CONNECTING) {
            return;
        }

        long timeoutNanos = configuration.connectTimeout().toNanos();
        StompConnection connection = await(
                driver.connect(configuration.host(), configuration.port()),
                timeoutNanos,
                "reconnecting STOMP client"
        );

        try {
            List<Subscription> subscriptions = await(worker.submit(() -> {
                if (status.get() != Status.CONNECTING) {
                    return List.of();
                }
                bind(connection);
                return subscriptionManager.subscriptions();
            }), timeoutNanos, "preparing STOMP reconnect");

            if (status.get() != Status.CONNECTING) {
                connection.disconnect();
                return;
            }

            long restoreDeadline = System.nanoTime() + timeoutNanos;
            for (Subscription subscription : subscriptions) {
                long remaining = restoreDeadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new ClientException("Timed out while restoring STOMP subscriptions");
                }
                await(connection.subscribe(
                        subscription.destination().path(),
                        subscription.stompHeaders().toMap(),
                        subscription.framesHandler()
                ), remaining, "restoring STOMP subscriptions");
            }

            boolean connected = await(worker.submit(() -> {
                if (!connection.isConnected()) {
                    throw new ClientException("STOMP connection closed while restoring subscriptions");
                }
                return status.compareAndSet(Status.CONNECTING, Status.CONNECTED);
            }), timeoutNanos, "completing STOMP reconnect");

            if (!connected) {
                connection.disconnect();
            }
        } catch (RuntimeException error) {
            connection.disconnect();
            throw error;
        }
    }

    /** Waits on the dedicated reconnect thread and normalizes JDK future failures. */
    private static <T> T await(
            CompletableFuture<T> future,
            long timeoutNanos,
            String operation
    ) {
        try {
            return future.get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ClientException("Interrupted while " + operation, error);
        } catch (TimeoutException error) {
            throw new ClientException("Timed out while " + operation, error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ClientException("Failed while " + operation, cause == null ? error : cause);
        }
    }

    private @Nullable StompConnection activeConnection() {
        return status.get() == Status.CONNECTED ? connection : null;
    }

    private void cancelReconnect() {
        RetryResult result = reconnectResult.getAndSet(null);
        if (result != null) {
            result.cancel();
        }
    }

    private boolean transitionToShuttingDown() {
        while (true) {
            Status current = status.get();
            if (current == Status.SHUTTING_DOWN || current == Status.SHUTDOWN) {
                return false;
            }
            if (status.compareAndSet(current, Status.SHUTTING_DOWN)) {
                return true;
            }
        }
    }

    private static <T> CompletableFuture<T> notConnected() {
        return CompletableFuture.failedFuture(new ClientException("STOMP client is not connected"));
    }

    private static void notifyHandler(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException error) {
            log.warn("Titan client handler failed", error);
        }
    }
}
