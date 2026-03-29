package org.traffichunter.titan.core.transport;

class ServerException extends TransportException {

    public ServerException() {
    }

    public ServerException(final String message) {
        super(message);
    }

    public ServerException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ServerException(final Throwable cause) {
        super(cause);
    }

    public ServerException(final String message, final Throwable cause, final boolean enableSuppression,
                           final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}