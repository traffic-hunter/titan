package org.traffichunter.titan.core.transport.stomp;

enum Status {
    INITIALIZED,
    STARTING,
    STARTED,
    CONNECTING,
    CONNECTED,
    SHUTTING_DOWN,
    SHUTDOWN,
    ;

    static boolean isRunning(Status status) {
        return switch (status) {
            case STARTING, STARTED, CONNECTING, CONNECTED -> true;
            case INITIALIZED, SHUTTING_DOWN, SHUTDOWN -> false;
        };
    }
}
