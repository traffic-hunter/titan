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
public class InetServerOption extends InetOption {

    public static final InetServerOption DEFAULT_INET_SERVER_OPTION = InetServerOption.builder().build();

    private final Map<SocketOption<?>, Object> childSocketOptions;

    private InetServerOption(
            Map<SocketOption<?>, Object> serverSocketOptions,
            Map<SocketOption<?>, Object> childSocketOptions
    ) {
        super(serverSocketOptions);
        this.childSocketOptions = Map.copyOf(childSocketOptions);
    }

    public Map<SocketOption<?>, Object> serverSocketOptions() {
        return socketOptions();
    }

    public Map<SocketOption<?>, Object> childSocketOptions() {
        return childSocketOptions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<SocketOption<?>, Object> serverOptions = new HashMap<>();
        private final Map<SocketOption<?>, Object> childOptions = new HashMap<>();

        public <T> Builder option(SocketOption<T> option, T value) {
            serverOptions.put(option, value);
            return this;
        }

        public <T> Builder childOption(SocketOption<T> option, T value) {
            childOptions.put(option, value);
            return this;
        }

        public Builder reuseAddress(boolean enabled) {
            return option(StandardSocketOptions.SO_REUSEADDR, enabled);
        }

        public Builder receiveBufferSize(int size) {
            return option(StandardSocketOptions.SO_RCVBUF, size);
        }

        public Builder childTcpNoDelay(boolean enabled) {
            return childOption(StandardSocketOptions.TCP_NODELAY, enabled);
        }

        public Builder childKeepAlive(boolean enabled) {
            return childOption(StandardSocketOptions.SO_KEEPALIVE, enabled);
        }

        public Builder childSendBufferSize(int size) {
            return childOption(StandardSocketOptions.SO_SNDBUF, size);
        }

        public Builder childReceiveBufferSize(int size) {
            return childOption(StandardSocketOptions.SO_RCVBUF, size);
        }

        public Builder childReuseAddress(boolean enabled) {
            return childOption(StandardSocketOptions.SO_REUSEADDR, enabled);
        }

        public InetServerOption build() {
            return new InetServerOption(serverOptions, childOptions);
        }
    }
}
