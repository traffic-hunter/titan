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
package org.traffichunter.titan.core.channel.stomp;

import org.traffichunter.titan.core.channel.ChannelHandShakeEventListener;
import org.traffichunter.titan.core.channel.NetServerChannel;
import org.traffichunter.titan.core.codec.stomp.StompFrame;
import org.traffichunter.titan.core.codec.stomp.StompServerSubscriptions;
import org.traffichunter.titan.core.util.concurrent.Promise;
import org.traffichunter.titan.core.transport.stomp.option.StompServerOption;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author yungwang-o
 */
public interface StompServerChannel extends StompChannel {

    static StompServerChannel open(
            ChannelHandShakeEventListener channelHandShakeEventListener,
            StompServerOption option
    ) {
        return new StompServerTcpChannel(channelHandShakeEventListener, option);
    }

    static StompServerChannel wrap(NetServerChannel serverChannel, StompServerOption option) {
        return new StompServerTcpChannel(serverChannel, option);
    }

    default Promise<Void> write(StompFrame frame) {
        return write(frame.toBuffer());
    }

    Promise<Void> write(Buffer buffer);

    void register(StompClientChannel connection);

    void unregister(String sessionId);

    void cleanUp(StompClientChannel connection);

    void cleanupInactiveConnections();

    @Nullable StompClientChannel findConnection(String sessionId);

    List<StompClientChannel> connections();

    StompClientChannel connection();

    StompServerSubscriptions subscriptions();

    StompServerOption option();
}
