package org.traffichunter.titan.core.event;

class EventLoopException extends RuntimeException {

    public EventLoopException() {}

    public EventLoopException(final String message) {
        super(message);
    }

    public EventLoopException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public EventLoopException(final Throwable cause) {
        super(cause);
    }

    public EventLoopException(final String message,
                              final Throwable cause,
                              final boolean enableSuppression,
                              final boolean writableStackTrace) {

        super(message, cause, enableSuppression, writableStackTrace);
    }
}