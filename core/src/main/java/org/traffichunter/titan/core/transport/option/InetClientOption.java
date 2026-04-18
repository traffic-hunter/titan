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
