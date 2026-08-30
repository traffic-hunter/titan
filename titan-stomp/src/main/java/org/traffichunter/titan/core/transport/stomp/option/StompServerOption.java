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
import org.traffichunter.titan.core.transport.option.InetServerOption;

public record StompServerOption(
        int maxFrameLength,
        int maxFrameInTransaction,
        String supportedVersions,
        boolean secured,
        boolean sendErrorOnNoSubscriptions,
        long ackTimeoutMillis,
        int timeFactor,
        long heartbeatX,
        long heartbeatY,
        int transactionChunkSize,
        int maxSubscriptionsByClient,
        StompVersion stompVersion,
        InetServerOption inetServerOption
) {

    public static final int DEFAULT_MAX_FRAME_LENGTH = 1024 * 1024;
    public static final int DEFAULT_MAX_FRAME_IN_TRANSACTION = 1000;
    public static final int DEFAULT_TRANSACTION_CHUNK_SIZE = 1000;
    public static final int DEFAULT_MAX_SUBSCRIPTIONS_BY_CLIENT = 1000;

    public static final String SUPPORTED_VERSION = "1.2";

    public StompServerOption {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("maxFrameLength must be greater than zero");
        }
        if (maxFrameInTransaction <= 0 || transactionChunkSize <= 0 || maxSubscriptionsByClient <= 0) {
            throw new IllegalArgumentException("Transaction/subscription limits must be greater than zero");
        }
        if (ackTimeoutMillis <= 0 || timeFactor <= 0 || heartbeatX < 0 || heartbeatY < 0) {
            throw new IllegalArgumentException("Timeout/time/heartbeat values are invalid");
        }
        if (supportedVersions.isBlank()) {
            throw new IllegalArgumentException("supportedVersions cannot be blank");
        }
        if (!SUPPORTED_VERSION.equals(supportedVersions)) {
            throw new IllegalArgumentException("Only STOMP 1.2 is supported");
        }
    }

    public static StompServerOption of(
            Integer maxFrameLength,
            Integer maxFrameInTransaction,
            String supportedVersions,
            Boolean secured,
            Boolean sendErrorOnNoSubscriptions,
            Long ackTimeoutMillis,
            Integer timeFactor,
            Long heartbeatX,
            Long heartbeatY,
            Integer transactionChunkSize,
            Integer maxSubscriptionsByClient,
            InetServerOption inetServerOption
    ) {
        return new StompServerOption(
                maxFrameLength == null ? DEFAULT_MAX_FRAME_LENGTH : maxFrameLength,
                maxFrameInTransaction == null ? DEFAULT_MAX_FRAME_IN_TRANSACTION : maxFrameInTransaction,
                supportedVersions == null ? SUPPORTED_VERSION : supportedVersions,
                secured != null && secured,
                sendErrorOnNoSubscriptions != null && sendErrorOnNoSubscriptions,
                ackTimeoutMillis == null ? 10_000L : ackTimeoutMillis,
                timeFactor == null ? 1 : timeFactor,
                heartbeatX == null ? 1000L : heartbeatX,
                heartbeatY == null ? 1000L : heartbeatY,
                transactionChunkSize == null ? DEFAULT_TRANSACTION_CHUNK_SIZE : transactionChunkSize,
                maxSubscriptionsByClient == null ? DEFAULT_MAX_SUBSCRIPTIONS_BY_CLIENT : maxSubscriptionsByClient,
                StompVersion.STOMP_1_2,
                inetServerOption == null ? InetServerOption.DEFAULT_INET_SERVER_OPTION : inetServerOption
        );
    }

    public static StompServerOptionBuilder builder() {
        return new StompServerOptionBuilder();
    }

    public static final class StompServerOptionBuilder {

        private Integer maxFrameLength;
        private Integer maxFrameInTransaction;
        private String supportedVersions;
        private Boolean secured;
        private Boolean sendErrorOnNoSubscriptions;
        private Long ackTimeoutMillis;
        private Integer timeFactor;
        private Long heartbeatX;
        private Long heartbeatY;
        private Integer transactionChunkSize;
        private Integer maxSubscriptionsByClient;
        private InetServerOption inetServerOption;

        private StompServerOptionBuilder() {
        }

        public StompServerOptionBuilder maxFrameLength(Integer value) {
            this.maxFrameLength = value;
            return this;
        }

        public StompServerOptionBuilder maxFrameInTransaction(Integer value) {
            this.maxFrameInTransaction = value;
            return this;
        }

        public StompServerOptionBuilder supportedVersions(String value) {
            this.supportedVersions = value;
            return this;
        }

        public StompServerOptionBuilder secured(Boolean value) {
            this.secured = value;
            return this;
        }

        public StompServerOptionBuilder sendErrorOnNoSubscriptions(Boolean value) {
            this.sendErrorOnNoSubscriptions = value;
            return this;
        }

        public StompServerOptionBuilder ackTimeoutMillis(Long value) {
            this.ackTimeoutMillis = value;
            return this;
        }

        public StompServerOptionBuilder timeFactor(Integer value) {
            this.timeFactor = value;
            return this;
        }

        public StompServerOptionBuilder heartbeatX(Long value) {
            this.heartbeatX = value;
            return this;
        }

        public StompServerOptionBuilder heartbeatY(Long value) {
            this.heartbeatY = value;
            return this;
        }

        public StompServerOptionBuilder transactionChunkSize(Integer value) {
            this.transactionChunkSize = value;
            return this;
        }

        public StompServerOptionBuilder maxSubscriptionsByClient(Integer value) {
            this.maxSubscriptionsByClient = value;
            return this;
        }

        public StompServerOptionBuilder inetServerOption(InetServerOption value) {
            this.inetServerOption = value;
            return this;
        }

        public StompServerOption build() {
            return StompServerOption.of(
                    maxFrameLength,
                    maxFrameInTransaction,
                    supportedVersions,
                    secured,
                    sendErrorOnNoSubscriptions,
                    ackTimeoutMillis,
                    timeFactor,
                    heartbeatX,
                    heartbeatY,
                    transactionChunkSize,
                    maxSubscriptionsByClient,
                    inetServerOption
            );
        }
    }
}
