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
package org.traffichunter.titan.core.transport.option;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yun
 */
public class InetClientOption extends InetOption {

    public static final InetClientOption DEFAULT_INET_CLIENT_OPTION = InetClientOption.builder().build();

    private InetClientOption(Map<SocketOption<?>, Object> socketOptions) {
        super(socketOptions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<SocketOption<?>, Object> options = new HashMap<>();

        public <T> Builder option(SocketOption<T> option, T value) {
            options.put(option, value);
            return this;
        }

        public Builder tcpNoDelay(boolean enabled) {
            return option(StandardSocketOptions.TCP_NODELAY, enabled);
        }

        public Builder keepAlive(boolean enabled) {
            return option(StandardSocketOptions.SO_KEEPALIVE, enabled);
        }

        public Builder reuseAddress(boolean enabled) {
            return option(StandardSocketOptions.SO_REUSEADDR, enabled);
        }

        public Builder sendBufferSize(int size) {
            return option(StandardSocketOptions.SO_SNDBUF, size);
        }

        public Builder receiveBufferSize(int size) {
            return option(StandardSocketOptions.SO_RCVBUF, size);
        }

        public Builder lingerSeconds(int seconds) {
            return option(StandardSocketOptions.SO_LINGER, seconds);
        }

        public InetClientOption build() {
            return new InetClientOption(options);
        }
    }
}
