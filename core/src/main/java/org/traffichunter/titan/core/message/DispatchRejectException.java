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
package org.traffichunter.titan.core.message;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.TitanException;

/**
 * Dispatch failure that can carry a confirmed queue admission refusal.
 *
 * <p>Constructors without a result do not establish whether the message entered the queue.
 * A caller must not infer a rejection code from the exception message.</p>
 *
 * @author yun
 */
public class DispatchRejectException extends TitanException {

    private final @Nullable DispatchResult result;

    public DispatchRejectException() {
        this.result = null;
    }

    public DispatchRejectException(String message, Object... args) {
        super(message, args);
        this.result = null;
    }

    public DispatchRejectException(String message) {
        super(message);
        this.result = null;
    }

    public DispatchRejectException(String message, Throwable cause) {
        super(message, cause);
        this.result = null;
    }

    public DispatchRejectException(Throwable cause) {
        super(cause);
        this.result = null;
    }

    public DispatchRejectException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.result = null;
    }

    /**
     * Creates a confirmed queue refusal with an SLF4J-style message template.
     *
     * @param result queue refusal, never {@link DispatchResult#ACCEPTED}
     * @param message message template using {@code {}} placeholders
     * @param args values substituted into the template
     */
    public DispatchRejectException(DispatchResult result, String message, Object... args) {
        super(message, args);
        this.result = Objects.requireNonNull(result, "result");
        if (result == DispatchResult.ACCEPTED) {
            throw new IllegalArgumentException("Accepted messages cannot be classified as rejected");
        }
    }

    /**
     * Returns the confirmed refusal, or {@code null} when admission is unspecified.
     */
    public @Nullable DispatchResult result() {
        return result;
    }
}
