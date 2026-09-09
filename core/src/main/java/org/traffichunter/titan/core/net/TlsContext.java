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
package org.traffichunter.titan.core.net;

import javax.net.ssl.SSLContext;

/**
 * Creates per-connection TLS handlers from shared key and trust material.
 *
 * @author yun
 */
public interface TlsContext {

    TlsHandler newHandler(String peerHost, int peerPort);

    TlsHandler newHandler(String peerHost, int peerPort, TlsTaskExecutor taskExecutor);

    /**
     * Returns the local role used by handlers created from this context.
     */
    TlsSide side();

    SSLContext sslContext();
}
