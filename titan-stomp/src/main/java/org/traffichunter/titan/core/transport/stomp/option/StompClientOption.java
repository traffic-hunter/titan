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
package org.traffichunter.titan.core.transport.stomp.option;

import lombok.Builder;
import org.traffichunter.titan.core.codec.stomp.StompVersion;
import org.traffichunter.titan.core.transport.option.InetClientOption;

public record StompClientOption(
        String host,
        int port,
        String login,
        String passcode,
        boolean autoComputeContentLength,
        boolean useStompFrame,
        boolean bypassHostHeader,
        long heartbeatX,
        long heartbeatY,
        String virtualHost,
        int maxFrameLength,
        StompVersion stompVersion,
        InetClientOption inetClientOption
) {

    public static final StompClientOption DEFAULT_STOMP_CLIENT_OPTION = StompClientOption.builder().build();

    public static final String DEFAULT_STOMP_HOST = "127.0.0.1";
    public static final int DEFAULT_STOMP_PORT = 61613;
    public static final int DEFAULT_MAX_FRAME_LENGTH = 65536;
    public static final long DEFAULT_HEARTBEAT_X = 1000L;
    public static final long DEFAULT_HEARTBEAT_Y = 1000L;

    public static final String SUPPORTED_VERSION = "1.2";

    public StompClientOption {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host cannot be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in range 1..65535");
        }
        if (heartbeatX < 0 || heartbeatY < 0) {
            throw new IllegalArgumentException("heartbeat values must be >= 0");
        }
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("maxFrameLength must be greater than zero");
        }
    }

    @Builder(builderMethodName = "builder")
    public static StompClientOption of(
            StompVersion version,
            String host,
            Integer port,
            String login,
            String passcode,
            Boolean autoComputeContentLength,
            Boolean useStompFrame,
            Boolean bypassHostHeader,
            Long heartbeatX,
            Long heartbeatY,
            String virtualHost,
            Integer maxFrameLength,
            InetClientOption inetClientOption
    ) {
        StompVersion resolvedVersion = version == null ? StompVersion.STOMP_1_2 : version;
        if (resolvedVersion != StompVersion.STOMP_1_2) {
            throw new IllegalArgumentException("Only STOMP 1.2 is supported");
        }

        return new StompClientOption(
                host ==  null ? DEFAULT_STOMP_HOST : host,
                port == null ? DEFAULT_STOMP_PORT : port,
                login,
                passcode,
                autoComputeContentLength == null || autoComputeContentLength,
                useStompFrame != null && useStompFrame,
                bypassHostHeader != null && bypassHostHeader,
                heartbeatX == null ? DEFAULT_HEARTBEAT_X : heartbeatX,
                heartbeatY == null ? DEFAULT_HEARTBEAT_Y : heartbeatY,
                virtualHost,
                maxFrameLength == null ? DEFAULT_MAX_FRAME_LENGTH : maxFrameLength,
                StompVersion.STOMP_1_2,
                inetClientOption
        );
    }
}
