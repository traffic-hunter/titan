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
import org.traffichunter.titan.core.transport.option.InetServerOption;

public record StompServerOption(
        int maxHeaderLength,
        int maxHeaders,
        int maxBodyLength,
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

    public static final int DEFAULT_MAX_HEADER_LENGTH = 1024 * 10;
    public static final int DEFAULT_MAX_HEADERS = 1000;
    public static final int DEFAULT_MAX_BODY_LENGTH = 1024 * 1024 * 100;
    public static final int DEFAULT_MAX_FRAME_IN_TRANSACTION = 1000;
    public static final int DEFAULT_TRANSACTION_CHUNK_SIZE = 1000;
    public static final int DEFAULT_MAX_SUBSCRIPTIONS_BY_CLIENT = 1000;

    public static final String SUPPORTED_VERSION = "1.2";

    public StompServerOption {
        if (maxHeaderLength <= 0 || maxHeaders <= 0 || maxBodyLength <= 0) {
            throw new IllegalArgumentException("Frame/header limits must be greater than zero");
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

    @Builder(builderMethodName = "builder")
    public static StompServerOption of(
            Integer maxHeaderLength,
            Integer maxHeaders,
            Integer maxBodyLength,
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
                maxHeaderLength == null ? DEFAULT_MAX_HEADER_LENGTH : maxHeaderLength,
                maxHeaders == null ? DEFAULT_MAX_HEADERS : maxHeaders,
                maxBodyLength == null ? DEFAULT_MAX_BODY_LENGTH : maxBodyLength,
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
                inetServerOption
        );
    }
}
