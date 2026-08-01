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
 * Public facade for Titan clients.
 *
 * <p>The nested builder is the only public client configuration surface. Transport-specific
 * implementations and their low-level STOMP options remain internal to the client module.</p>
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
     * Sends a STOMP message using the default headers.
     *
     * @param destination target STOMP destination
     * @param payload message payload
     * @return the asynchronous transport result
     */
    CompletableFuture<StompFrames> send(String destination, Buffer payload);

    /**
     * Sends a STOMP message with additional headers.
     *
     * @param destination target STOMP destination
     * @param payload message payload
     * @param headers additional STOMP headers
     * @return the asynchronous transport result
     */
    CompletableFuture<StompFrames> send(String destination, Buffer payload, Map<Elements, String> headers);

    /**
     * Subscribes with transport defaults and invokes the handler for delivered MESSAGE frames.
     *
     * @return a future containing the assigned subscription identifier
     */
    CompletableFuture<String> subscribe(String destination, Handler<StompFrames> handler);

    /**
     * Subscribes with explicit STOMP headers and invokes the handler for delivered MESSAGE frames.
     *
     * @return a future containing the assigned subscription identifier
     */
    CompletableFuture<String> subscribe(
            String destination,
            Map<Elements, String> headers,
            Handler<StompFrames> handler
    );

    /** Removes the subscription identified by {@code subscriptionId}. */
    CompletableFuture<StompFrames> unsubscribe(String subscriptionId);

    /** Removes a subscription while adding the supplied STOMP headers to the UNSUBSCRIBE frame. */
    CompletableFuture<StompFrames> unsubscribe(String subscriptionId, Map<Elements, String> headers);

    /** Acknowledges a message for subscriptions using a client acknowledgement mode. */
    CompletableFuture<StompFrames> ack(String messageId);

    /** Negatively acknowledges a message for subscriptions using a client acknowledgement mode. */
    CompletableFuture<StompFrames> nack(String messageId);

    /** Performs a graceful STOMP disconnect without shutting down the client runtime. */
    CompletableFuture<StompFrames> disconnect();

    /** Registers the handler for STOMP ERROR frames. */
    @CanIgnoreReturnValue
    TitanClient errorHandler(Handler<StompFrames> handler);

    /** Registers the handler invoked after the active connection closes. */
    @CanIgnoreReturnValue
    TitanClient closeHandler(Handler<TitanClient> handler);

    /** Registers the handler invoked when an established connection is lost unexpectedly. */
    @CanIgnoreReturnValue
    TitanClient connectionDroppedHandler(Handler<TitanClient> handler);

    /** Registers the handler invoked when a STOMP heartbeat is received. */
    @CanIgnoreReturnValue
    TitanClient pingHandler(Handler<TitanClient> handler);

    /** Registers the handler for asynchronous transport and protocol failures. */
    @CanIgnoreReturnValue
    TitanClient exceptionHandler(Handler<Throwable> handler);

    /** Returns whether the facade currently owns an active STOMP connection. */
    boolean isConnected();

    /** Returns whether the client runtime has been started. */
    boolean isStarted();

    /** Returns whether the client runtime has completed shutdown. */
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

        /** Selects the client implementation. The default is {@link Implementation#TITAN}. */
        @CanIgnoreReturnValue
        Builder implementation(Implementation implementation);

        /**
         * Sets the number of secondary I/O event loops owned by the native client.
         *
         * <p>The value must be greater than zero. Vert.x manages its own event loops, so this
         * setting is ignored when {@link Implementation#VERTX} is selected.</p>
         */
        @CanIgnoreReturnValue
        Builder worker(int workers);

        /** Sets the remote server host. The default is {@code 127.0.0.1}. */
        @CanIgnoreReturnValue
        Builder host(String host);

        /** Sets the remote server port. The default is {@code 61613}. */
        @CanIgnoreReturnValue
        Builder port(int port);

        /** Sets STOMP framing, CONNECT headers, heartbeat, and protocol negotiation options. */
        @CanIgnoreReturnValue
        Builder session(StompSessionOption option);

        /** Sets the maximum duration allowed for a single connection attempt. */
        @CanIgnoreReturnValue
        Builder connectTimeout(Duration connectTimeout);

        /** Sets the reconnect delay and attempt policy while retaining the no-op listener. */
        @CanIgnoreReturnValue
        Builder reconnect(RetryPolicy policy);

        /** Sets both reconnect policy and lifecycle listener. */
        @CanIgnoreReturnValue
        Builder reconnect(RetryPolicy policy, RetryListener listener);

        /** Configures native socket options and corresponding Vert.x socket settings. */
        @CanIgnoreReturnValue
        Builder inetOption(InetClientOption option);

        /** Selects WebSocket transport and records the HTTP upgrade path. */
        @CanIgnoreReturnValue
        Builder webSocket(String path);

        /**
         * Configures the client-side TLS context applied while the native transport is built.
         *
         * @throws UnsupportedOperationException when the Vert.x implementation is selected
         */
        @CanIgnoreReturnValue
        Builder tls(TlsContext context);

        /** Creates a new unstarted client. */
        TitanClient build();
    }
}
