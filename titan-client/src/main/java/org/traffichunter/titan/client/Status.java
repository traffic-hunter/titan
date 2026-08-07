package org.traffichunter.titan.client;

/**
 * Internal lifecycle states shared by Titan client implementations.
 *
 * <p>Connection loss moves an active client back through {@link #CONNECTING}; reconnect does not
 * introduce a separate public state. Shutdown states are terminal for the client instance.</p>
 *
 * @author yun
 */
enum Status {
    /** Runtime resources have not been started. */
    INITIALIZED,
    /** Event loops or Vert.x resources are starting. */
    STARTING,
    /** Runtime is available but no STOMP connection is active. */
    STARTED,
    /** Initial connection or reconnect negotiation is in progress. */
    CONNECTING,
    /** STOMP negotiation completed and operations can be sent. */
    CONNECTED,
    /** Shutdown has started and new operations are rejected. */
    SHUTTING_DOWN,
    /** Runtime resources have been released. */
    SHUTDOWN,
    ;

    /** Returns whether the runtime is started and has not entered shutdown. */
    static boolean isRunning(Status status) {
        return switch (status) {
            case STARTING, STARTED, CONNECTING, CONNECTED -> true;
            case INITIALIZED, SHUTTING_DOWN, SHUTDOWN -> false;
        };
    }
}
