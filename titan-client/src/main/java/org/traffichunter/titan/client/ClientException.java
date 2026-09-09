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
package org.traffichunter.titan.client;

/**
 * Reports lifecycle, configuration, or transport failures exposed by the client facade.
 *
 * <p>Transport-specific failures may be retained as the cause, allowing callers to depend on one
 * client-level exception without losing the original diagnostic information.</p>
 *
 * @author yun
 */
public class ClientException extends RuntimeException {

    /**
     * Creates a client exception with a diagnostic message.
     *
     * @param message description of the client failure
     */
    public ClientException(String message) {
        super(message);
    }

    /**
     * Creates a client exception retaining the transport or protocol failure that caused it.
     *
     * @param message description of the client failure
     * @param cause underlying failure
     */
    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
