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
package org.traffichunter.titan.core.codec.websocket;

import org.traffichunter.titan.core.codec.CodecException;

/**
 * @author yun
 */
public class WebSocketFrameException extends CodecException {

    static final int PROTOCOL_ERROR = 1002;
    static final int INVALID_PAYLOAD_DATA = 1007;
    static final int MESSAGE_TOO_BIG = 1009;

    private final int closeStatus;

    public WebSocketFrameException(String message) {
        this(PROTOCOL_ERROR, message);
    }

    WebSocketFrameException(int closeStatus, String message) {
        super(message);
        this.closeStatus = closeStatus;
    }

    /**
     * Returns the RFC 6455 close status that reports this failure to the peer.
     */
    public int closeStatus() {
        return closeStatus;
    }
}
