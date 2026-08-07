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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.net.TlsContext;
import org.traffichunter.titan.core.codec.stomp.StompHeaders.Elements;
import org.traffichunter.titan.core.resilience.retry.RetryListener;
import org.traffichunter.titan.core.resilience.retry.RetryPolicy;
import org.traffichunter.titan.core.transport.option.InetClientOption;
import org.traffichunter.titan.core.transport.stomp.option.StompSessionOption;
import org.traffichunter.titan.core.util.Handler;
import org.traffichunter.titan.core.util.buffer.Buffer;

/**
 * Transport-neutral public facade for Titan messaging clients.
 *
 * <p>The nested builder is the preferred configuration surface and hides the selected networking
 * implementation from application code. Advanced integrations may provide a
 * {@link StompClientDriver} directly to {@link DefaultTitanClient}, while normal users interact
 * only with this interface and transport-neutral {@link StompFrames} values.</p>
 *
 * @author yun
 */
public interface TitanClient {

    /**
     * Creates a builder initialized for Titan's native client with one I/O worker.
     *
     * @return a new independent client builder
     */
    static Builder builder() {
        return new DefaultTitanClientBuilder();
    }

    /**
     * Returns the selected client implementation name, such as {@code titan} or {@code vertx}.
     *
     * @return stable implementation name
     */
    String name();

    /** Starts the client runtime and its owned resources without opening a server connection. */
    void start();

    /**
     * Opens the configured STOMP connection.
     *
     * @return a future completed with this facade after STOMP negotiation succeeds
     */
    CompletableFuture<TitanClient> connect();

    /**
     * Sends a UTF-8 string payload using the default STOMP headers.
     *
     * <p>This convenience overload allocates the transport buffer and delegates to
     * {@link #send(String, Buffer)}.</p>
     *
     * @param destination target STOMP destination
     * @param payload string payload
     * @return the asynchronous transport result
     */
    default CompletableFuture<StompFrames> send(String destination, String payload) {
        return send(destination, Buffer.alloc(payload));
    }

    /**
     * Sends a STOMP message using the default headers.
     *
     * <p>Calling this method transfers ownership of {@code payload} to the client. The client
     * consumes exactly one reference whether the operation succeeds, returns a failed future, or
     * rejects the request synchronously. The caller must not access or release the buffer after
     * invocation.</p>
     *
     * @param destination target STOMP destination
     * @param payload message payload whose ownership is transferred to the client
     * @return the asynchronous transport result
     */
    CompletableFuture<StompFrames> send(String destination, Buffer payload);

    /**
     * Sends a STOMP message with additional headers.
     *
     * <p>This overload follows the same ownership-transfer contract as
     * {@link #send(String, Buffer)}.</p>
     *
     * @param destination target STOMP destination
     * @param payload message payload whose ownership is transferred to the client
     * @param headers additional STOMP headers
     * @return the asynchronous transport result
     */
    CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers);

    /**
     * Subscribes with transport defaults and invokes the handler for delivered MESSAGE frames.
     *
     * @param destination destination to subscribe to
     * @param handler handler for received MESSAGE frames
     * @return a future containing the assigned subscription identifier
     */
    CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler);

    /**
     * Subscribes with explicit STOMP headers and invokes the handler for delivered MESSAGE frames.
     *
     * @param destination destination to subscribe to
     * @param headers additional SUBSCRIBE headers
     * @param handler handler for received MESSAGE frames
     * @return a future containing the assigned subscription identifier
     */
    CompletableFuture<String> subscribe(
            String destination,
            Map<Elements, String> headers,
            Handler<StompFrames> handler
    );

    /**
     * Removes the subscription identified by {@code subscriptionId}.
     *
     * @param subscriptionId identifier returned by subscribe
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> unsubscribe(String subscriptionId);

    /**
     * Removes a subscription while adding the supplied STOMP headers to the UNSUBSCRIBE frame.
     *
     * @param subscriptionId identifier returned by subscribe
     * @param headers additional UNSUBSCRIBE headers
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers);

    /**
     * Acknowledges a message for subscriptions using a client acknowledgement mode.
     *
     * @param messageId identifier of the received message
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> ack(String messageId);

    /**
     * Negatively acknowledges a message for subscriptions using a client acknowledgement mode.
     *
     * @param messageId identifier of the received message
     * @return future completed with the resulting transport frame
     */
    CompletableFuture<StompFrames> nack(String messageId);

    /**
     * Performs a graceful STOMP disconnect without shutting down the client runtime.
     *
     * @return future completed after the disconnect operation is issued
     */
    CompletableFuture<StompFrames> disconnect();

    /**
     * Registers the handler for STOMP ERROR frames.
     *
     * @param handler handler to invoke for ERROR frames
     * @return this client
     */
    @CanIgnoreReturnValue
    TitanClient errorHandler(Handler<StompFrames> handler);

    /**
     * Registers the handler invoked after the active connection closes.
     *
     * @param handler close notification handler
     * @return this client
     */
    @CanIgnoreReturnValue
    TitanClient closeHandler(Handler<TitanClient> handler);

    /**
     * Registers the handler invoked when an established connection is lost unexpectedly.
     *
     * @param handler connection-loss notification handler
     * @return this client
     */
    @CanIgnoreReturnValue
    TitanClient connectionDroppedHandler(Handler<TitanClient> handler);

    /**
     * Registers the handler invoked when a STOMP heartbeat is received.
     *
     * @param handler heartbeat notification handler
     * @return this client
     */
    @CanIgnoreReturnValue
    TitanClient pingHandler(Handler<TitanClient> handler);

    /**
     * Registers the handler for asynchronous transport and protocol failures.
     *
     * @param handler asynchronous failure handler
     * @return this client
     */
    @CanIgnoreReturnValue
    TitanClient exceptionHandler(Handler<Throwable> handler);

    /**
     * Returns whether the facade currently owns an active STOMP connection.
     *
     * @return {@code true} when messaging operations can be issued
     */
    boolean isConnected();

    /**
     * Returns whether the client runtime has been started.
     *
     * @return {@code true} after start and before shutdown begins
     */
    boolean isStarted();

    /**
     * Returns whether the client runtime has completed shutdown.
     *
     * @return {@code true} after all owned resources have been released
     */
    boolean isShutdown();

    /**
     * Stops reconnect work, closes the active connection, and releases owned runtime resources.
     *
     * @param timeout maximum graceful shutdown duration
     * @param unit unit of {@code timeout}
     */
    void shutdown(long timeout, TimeUnit unit);

    /** Selects the hidden networking implementation used by the facade. */
    enum Implementation {
        /** Titan's native channel and event-loop implementation. */
        TITAN,
        /** Vert.x STOMP client adapter. */
        VERTX
    }

    /**
     * Builds a transport-neutral {@link TitanClient} while keeping implementation options internal.
     */
    interface Builder {

        /**
         * Selects the client implementation. The default is {@link Implementation#TITAN}.
         *
         * @param implementation networking implementation to use
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder implementation(Implementation implementation);

        /**
         * Sets the number of secondary I/O event loops owned by the native client.
         *
         * <p>The value must be greater than zero. Vert.x manages its own event loops, so this
         * setting is ignored when {@link Implementation#VERTX} is selected.</p>
         *
         * @param workers number of native I/O workers
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder worker(int workers);

        /**
         * Sets the remote server host. The default is {@code 127.0.0.1}.
         *
         * @param host remote server host
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder host(String host);

        /**
         * Sets the remote server port. The default is {@code 61613}.
         *
         * @param port remote server port
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder port(int port);

        /**
         * Sets STOMP framing, CONNECT headers, heartbeat, and protocol negotiation options.
         *
         * @param option STOMP session settings
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder session(StompSessionOption option);

        /**
         * Sets the maximum duration allowed for a single connection attempt.
         *
         * @param connectTimeout connection-attempt timeout
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder connectTimeout(Duration connectTimeout);

        /**
         * Sets the reconnect delay and attempt policy while retaining the no-op listener.
         *
         * @param policy reconnect retry policy
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder reconnect(RetryPolicy policy);

        /**
         * Sets both reconnect policy and lifecycle listener.
         *
         * @param policy reconnect retry policy
         * @param listener listener notified for retry lifecycle events
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder reconnect(RetryPolicy policy, RetryListener listener);

        /**
         * Configures native socket options and corresponding Vert.x socket settings.
         *
         * @param option socket and transport settings
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder inetOption(InetClientOption option);

        /**
         * Selects WebSocket transport and records the HTTP upgrade path.
         *
         * @param path absolute WebSocket endpoint path
         * @return this builder
         */
        @CanIgnoreReturnValue
        Builder webSocket(String path);

        /**
         * Configures the client-side TLS context applied while the native transport is built.
         *
         * @param context initialized client-side TLS context
         * @return this builder
         * @throws UnsupportedOperationException when the Vert.x implementation is selected
         */
        @CanIgnoreReturnValue
        Builder tls(TlsContext context);

        /**
         * Builds a new client facade without starting its runtime.
         *
         * @return configured Titan client
         */
        TitanClient build();
    }
}
