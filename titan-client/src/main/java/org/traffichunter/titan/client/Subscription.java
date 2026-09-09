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

import org.traffichunter.titan.core.codec.stomp.StompFrames;
import org.traffichunter.titan.core.codec.stomp.StompHeaders;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Handler;

/**
 * Logical subscription retained independently of one physical STOMP connection.
 *
 * <p>The client stores this metadata after SUBSCRIBE succeeds. On a replacement connection,
 * it reuses the destination, headers, and frame handler to restore the subscription before
 * returning to the connected state.</p>
 *
 * @param id stable subscription identifier used for unsubscribe and reconnect
 * @param destination logical destination to restore
 * @param stompHeaders headers sent when creating the subscription
 * @param framesHandler handler retained across physical connections
 *
 * @author yun
 */
public record Subscription(
        String id,
        Destination destination,
        StompHeaders stompHeaders,
        Handler<StompFrames> framesHandler
) {
}
