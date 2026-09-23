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
package org.traffichunter.titan.core.channel;

import org.jspecify.annotations.Nullable;

/**
 * Typed outcome of a write the transport did not complete.
 *
 * <p>Carries no stack trace: a close fails every queued write at once, and callers act on the
 * reason, not on where it was raised.</p>
 *
 * @author yun
 */
public final class ChannelWriteException extends ChannelException {

    public enum Reason {
        /** Refused or discarded before any byte reached the socket. */
        NOT_SENT,
        /** Some bytes may have reached the socket before the channel closed. Do not resend. */
        UNKNOWN
    }

    private final Reason reason;

    public ChannelWriteException(Reason reason, String message) {
        this(reason, message, null);
    }

    public ChannelWriteException(Reason reason, String message, @Nullable Throwable cause) {
        super(message, cause, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
