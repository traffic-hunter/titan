package org.traffichunter.titan.core.transport;

class ClientException extends TransportException {

        public ClientException() {}

        public ClientException(String message) { super(message); }

        public ClientException(String message, Throwable cause) { super(message, cause); }

        public ClientException(Throwable cause) { super(cause); }

        public ClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }