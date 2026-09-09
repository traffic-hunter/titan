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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for core event-loop implementations.
 *
 * <p>The factory centralizes thread naming for primary and secondary I/O loops.</p>
 *
 * @author yungwang-o
 */
public final class EventLoopFactory {

    private static final Logger log = LoggerFactory.getLogger(EventLoopFactory.class);

    public static ChannelPrimaryIOEventLoop createPrimaryIOEventLoop() {
        return new ChannelPrimaryIOEventLoop();
    }

    public static ChannelSecondaryIOEventLoop createSecondaryIOEventLoop(int eventLoopNameCount) {
        return new ChannelSecondaryIOEventLoop(nameEventLoop(eventLoopNameCount));
    }

    private static String nameEventLoop(int eventLoopNameCount) {
        return EventLoopConstants.SECONDARY_EVENT_LOOP_THREAD_NAME + "-" + eventLoopNameCount;
    }

    private EventLoopFactory() {}
}
