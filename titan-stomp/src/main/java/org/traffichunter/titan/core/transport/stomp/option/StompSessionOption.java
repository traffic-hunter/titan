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

import org.traffichunter.titan.core.codec.stomp.StompVersion;

/**
 * Configures STOMP framing and session negotiation independently of a client runtime.
 *
 * <p>Outbound client channels and accepted server child channels can use the same settings.
 * The transport runtime configures remote ports, socket options, connection timeouts, and
 * reconnect policies separately.</p>
 *
 * @param login optional CONNECT login header
 * @param passcode optional CONNECT passcode header
 * @param autoComputeContentLength whether outbound frames calculate {@code content-length}
 * @param useStompFrame whether adapters may use their native STOMP frame representation
 * @param bypassHostHeader whether the CONNECT host header is omitted
 * @param heartbeatX outgoing heartbeat capability in milliseconds
 * @param heartbeatY requested incoming heartbeat interval in milliseconds
 * @param virtualHost optional virtual host used during STOMP negotiation
 * @param maxFrameLength maximum decoded frame size in bytes
 * @param version STOMP protocol version used by the channel
 *
 * @author yun
 */
public record StompSessionOption(
        String login,
        String passcode,
        boolean autoComputeContentLength,
        boolean useStompFrame,
        boolean bypassHostHeader,
        long heartbeatX,
        long heartbeatY,
        String virtualHost,
        int maxFrameLength,
        StompVersion version
) {
    public static final int DEFAULT_MAX_FRAME_LENGTH = 65536;
    public static final long DEFAULT_HEARTBEAT_X = 1000L;
    public static final long DEFAULT_HEARTBEAT_Y = 1000L;
    public static final StompSessionOption DEFAULT = builder().build();

    public StompSessionOption {
        if (heartbeatX < 0 || heartbeatY < 0) {
            throw new IllegalArgumentException("heartbeat values must be >= 0");
        }
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("maxFrameLength must be greater than zero");
        }
    }

    /**
     * Resolves nullable builder inputs to stable protocol defaults.
     *
     * <p>STOMP 1.2 is currently the only accepted version. Numeric validation is completed by
     * the record's canonical constructor after defaults have been applied.</p>
     */
    public static StompSessionOption of(
            StompVersion version,
            String login,
            String passcode,
            Boolean autoComputeContentLength,
            Boolean useStompFrame,
            Boolean bypassHostHeader,
            Long heartbeatX,
            Long heartbeatY,
            String virtualHost,
            Integer maxFrameLength
    ) {
        StompVersion resolvedVersion = version == null ? StompVersion.STOMP_1_2 : version;
        if (resolvedVersion != StompVersion.STOMP_1_2) {
            throw new IllegalArgumentException("Only STOMP 1.2 is supported");
        }

        return new StompSessionOption(
                login,
                passcode,
                autoComputeContentLength == null || autoComputeContentLength,
                useStompFrame != null && useStompFrame,
                bypassHostHeader != null && bypassHostHeader,
                heartbeatX == null ? DEFAULT_HEARTBEAT_X : heartbeatX,
                heartbeatY == null ? DEFAULT_HEARTBEAT_Y : heartbeatY,
                virtualHost,
                maxFrameLength == null ? DEFAULT_MAX_FRAME_LENGTH : maxFrameLength,
                resolvedVersion
        );
    }

    public static StompSessionOptionBuilder builder() {
        return new StompSessionOptionBuilder();
    }

    public static final class StompSessionOptionBuilder {

        private StompVersion version;
        private String login;
        private String passcode;
        private Boolean autoComputeContentLength;
        private Boolean useStompFrame;
        private Boolean bypassHostHeader;
        private Long heartbeatX;
        private Long heartbeatY;
        private String virtualHost;
        private Integer maxFrameLength;

        private StompSessionOptionBuilder() {
        }

        public StompSessionOptionBuilder version(StompVersion value) {
            this.version = value;
            return this;
        }

        public StompSessionOptionBuilder login(String value) {
            this.login = value;
            return this;
        }

        public StompSessionOptionBuilder passcode(String value) {
            this.passcode = value;
            return this;
        }

        public StompSessionOptionBuilder autoComputeContentLength(Boolean value) {
            this.autoComputeContentLength = value;
            return this;
        }

        public StompSessionOptionBuilder useStompFrame(Boolean value) {
            this.useStompFrame = value;
            return this;
        }

        public StompSessionOptionBuilder bypassHostHeader(Boolean value) {
            this.bypassHostHeader = value;
            return this;
        }

        public StompSessionOptionBuilder heartbeatX(Long value) {
            this.heartbeatX = value;
            return this;
        }

        public StompSessionOptionBuilder heartbeatY(Long value) {
            this.heartbeatY = value;
            return this;
        }

        public StompSessionOptionBuilder virtualHost(String value) {
            this.virtualHost = value;
            return this;
        }

        public StompSessionOptionBuilder maxFrameLength(Integer value) {
            this.maxFrameLength = value;
            return this;
        }

        public StompSessionOption build() {
            return StompSessionOption.of(
                    version,
                    login,
                    passcode,
                    autoComputeContentLength,
                    useStompFrame,
                    bypassHostHeader,
                    heartbeatX,
                    heartbeatY,
                    virtualHost,
                    maxFrameLength
            );
        }
    }
}
