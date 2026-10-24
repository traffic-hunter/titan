/*
 * Copyright 2025 traffic-hunter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traffichunter.titan.core.transport.websocket;

/**
 * @author yun
 */
public class WebSocketHandshakeException extends RuntimeException {

    static final int BAD_REQUEST = 400;
    static final int NOT_FOUND = 404;
    static final int UPGRADE_REQUIRED = 426;
    static final int REQUEST_HEADER_FIELDS_TOO_LARGE = 431;

    private final int status;

    public WebSocketHandshakeException(String message) {
        this(BAD_REQUEST, message);
    }

    public WebSocketHandshakeException(String message, Throwable cause) {
        super(message, cause);
        this.status = BAD_REQUEST;
    }

    WebSocketHandshakeException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status a server answers this failure with.
     */
    public int status() {
        return status;
    }
}
