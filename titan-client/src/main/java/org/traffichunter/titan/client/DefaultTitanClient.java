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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.resilience.retry.RetryExecutor;
import org.traffichunter.titan.core.resilience.retry.RetryExecutors;
import org.traffichunter.titan.core.resilience.retry.RetryResult;
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
    private final AtomicReference<Status> status = new AtomicReference<>(Status.INITIALIZED);
    private final AtomicReference<@Nullable RetryResult> reconnectResult = new AtomicReference<>();

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
     */
    public DefaultTitanClient(StompClientDriver driver) {
        this.driver = driver;
        this.configuration = driver.clientConfiguration();
        this.reconnectExecutor = RetryExecutors.eventLoopRetryExecutor(
                configuration.reconnectPolicy(),
                configuration.reconnectListener()
        );
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

        return connectionFuture.thenApply(connection -> {
                    if (status.get() != Status.CONNECTING) {
                        connection.disconnect();
                        throw new ClientException("Client stopped while connecting");
                    }

                    bind(connection);
                    if (!status.compareAndSet(Status.CONNECTING, Status.CONNECTED)) {
                        connection.disconnect();
                        throw new ClientException("Client stopped while connecting");
                    }
                    return (TitanClient) this;
                })
                .whenComplete((client, error) -> {
                    if (error != null) {
                        status.compareAndSet(Status.CONNECTING, Status.STARTED);
                    }
                });
    }

    @Override
    public CompletableFuture<StompFrames> send(String destination, Buffer payload) {
        StompConnection connection = activeConnection();
        return connection == null ? notConnected() : connection.send(destination, payload);
    }

    @Override
    public CompletableFuture<StompFrames> send(
            String destination,
            Buffer payload,
            Map<Elements, String> headers
    ) {
        StompConnection connection = activeConnection();
        return connection == null
                ? notConnected()
                : connection.send(destination, payload, headers);
    }

    @Override
    public CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler) {
        StompConnection connection = activeConnection();
        return connection == null
                ? notConnected()
                : connection.subscribe(destination, handler);
    }

    @Override
    public CompletableFuture<String> subscribe(
            String destination,
            Map<Elements, String> headers,
            Handler<StompFrames> handler
    ) {
        StompConnection connection = activeConnection();
        return connection == null
                ? notConnected()
                : connection.subscribe(destination, headers, handler);
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(String subscriptionId) {
        StompConnection connection = activeConnection();
        return connection == null ? notConnected() : connection.unsubscribe(subscriptionId);
    }

    @Override
    public CompletableFuture<StompFrames> unsubscribe(
            String subscriptionId,
            Map<Elements, String> headers
    ) {
        StompConnection connection = activeConnection();
        return connection == null
                ? notConnected()
                : connection.unsubscribe(subscriptionId, headers);
    }

    @Override
    public CompletableFuture<StompFrames> ack(String messageId) {
        StompConnection connection = activeConnection();
        return connection == null ? notConnected() : connection.ack(messageId);
    }

    @Override
    public CompletableFuture<StompFrames> nack(String messageId) {
        StompConnection connection = activeConnection();
        return connection == null ? notConnected() : connection.nack(messageId);
    }

    @Override
    public CompletableFuture<StompFrames> disconnect() {
        StompConnection connection = this.connection;
        if (connection == null || !status.compareAndSet(Status.CONNECTED, Status.STARTED)) {
            return notConnected();
        }

        cancelReconnect();
        return connection.disconnect()
                .whenComplete((frame, error) -> {
                    if (error != null && connection.isConnected()) {
                        status.compareAndSet(Status.STARTED, Status.CONNECTED);
                    }
                });
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
        connection.errorHandler(frame -> notifyHandler(() -> errorHandler.handle(frame)));
        connection.closeHandler(ignored -> {
            if (this.connection != connection) {
                return;
            }
            handleConnectionLoss();
            notifyHandler(() -> closeHandler.handle(this));
        });
        connection.connectionDroppedHandler(ignored -> {
            if (this.connection != connection) {
                return;
            }
            handleConnectionLoss();
            notifyHandler(() -> connectionDroppedHandler.handle(this));
        });
        connection.pingHandler(ignored -> {
            if (this.connection == connection) {
                notifyHandler(() -> pingHandler.handle(this));
            }
        });
        connection.exceptionHandler(error -> {
            if (this.connection == connection) {
                notifyHandler(() -> exceptionHandler.handle(error));
            }
        });
    }

    private void handleConnectionLoss() {
        if (!status.compareAndSet(Status.CONNECTED, Status.CONNECTING)) {
            return;
        }

        RetryResult result = reconnectExecutor.retry(() -> {
            if (status.get() != Status.CONNECTING) {
                return Boolean.TRUE;
            }

            StompConnection connection;
            try {
                connection = driver.connect(configuration.host(), configuration.port()).get();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ClientException("Interrupted while reconnecting STOMP client", error);
            } catch (ExecutionException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception retryable) {
                    throw retryable;
                }
                throw new ClientException("Failed to reconnect STOMP client", cause == null ? error : cause);
            }

            if (status.get() != Status.CONNECTING) {
                connection.disconnect();
                return Boolean.TRUE;
            }
            bind(connection);
            if (!status.compareAndSet(Status.CONNECTING, Status.CONNECTED)) {
                connection.disconnect();
            }
            return Boolean.TRUE;
        });

        RetryResult previous = reconnectResult.getAndSet(result);
        if (previous != null) {
            previous.cancel();
        }
        if (status.get() != Status.CONNECTING && reconnectResult.compareAndSet(result, null)) {
            result.cancel();
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
