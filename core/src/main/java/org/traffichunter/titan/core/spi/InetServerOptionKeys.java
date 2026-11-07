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
package org.traffichunter.titan.core.spi;

import java.util.List;
import java.util.Map;
import org.traffichunter.titan.core.transport.option.InetServerOption;
import org.traffichunter.titan.core.util.OptionKey;

/**
 * Socket options shared by the built-in server providers.
 */
public final class InetServerOptionKeys {

    public static final OptionKey<Boolean> REUSE_ADDRESS = OptionKey.booleanKey("reuse-address");
    public static final OptionKey<Integer> RECEIVE_BUFFER_SIZE = OptionKey.intKey("receive-buffer-size");
    public static final OptionKey<Boolean> CHILD_TCP_NO_DELAY = OptionKey.booleanKey("child-tcp-no-delay");
    public static final OptionKey<Boolean> CHILD_KEEP_ALIVE = OptionKey.booleanKey("child-keep-alive");
    public static final OptionKey<Integer> CHILD_SEND_BUFFER_SIZE = OptionKey.intKey("child-send-buffer-size");
    public static final OptionKey<Integer> CHILD_RECEIVE_BUFFER_SIZE = OptionKey.intKey("child-receive-buffer-size");
    public static final OptionKey<Boolean> CHILD_REUSE_ADDRESS = OptionKey.booleanKey("child-reuse-address");

    public static final List<OptionKey<?>> ALL = List.of(
            REUSE_ADDRESS,
            RECEIVE_BUFFER_SIZE,
            CHILD_TCP_NO_DELAY,
            CHILD_KEEP_ALIVE,
            CHILD_SEND_BUFFER_SIZE,
            CHILD_RECEIVE_BUFFER_SIZE,
            CHILD_REUSE_ADDRESS
    );

    private InetServerOptionKeys() {
    }

    public static InetServerOption inetServerOption(final Map<String, String> options) {
        InetServerOption.Builder builder = InetServerOption.builder()
                .reuseAddress(REUSE_ADDRESS.get(options, true))
                .childTcpNoDelay(CHILD_TCP_NO_DELAY.get(options, true))
                .childKeepAlive(CHILD_KEEP_ALIVE.get(options, false))
                .childReuseAddress(CHILD_REUSE_ADDRESS.get(options, true));

        Integer receiveBufferSize = RECEIVE_BUFFER_SIZE.get(options);
        Integer childSendBufferSize = CHILD_SEND_BUFFER_SIZE.get(options);
        Integer childReceiveBufferSize = CHILD_RECEIVE_BUFFER_SIZE.get(options);

        if (receiveBufferSize != null) {
            builder.receiveBufferSize(receiveBufferSize);
        }
        if (childSendBufferSize != null) {
            builder.childSendBufferSize(childSendBufferSize);
        }
        if (childReceiveBufferSize != null) {
            builder.childReceiveBufferSize(childReceiveBufferSize);
        }

        return builder.build();
    }
}
