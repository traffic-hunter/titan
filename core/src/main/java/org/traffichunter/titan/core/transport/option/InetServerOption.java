/*
The MIT License

Copyright (c) 2025 traffic-hunter

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
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
