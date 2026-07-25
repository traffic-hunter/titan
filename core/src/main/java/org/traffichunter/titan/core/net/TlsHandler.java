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
package org.traffichunter.titan.core.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.channel.ChannelInBoundHandlerChain;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandler;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.codec.ChannelDecoder;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.Promise;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

/**
 * @author yun
 */
public abstract class TlsHandler extends ChannelDecoder implements ChannelOutBoundHandler {

    private static final Logger log = LoggerFactory.getLogger(TlsHandler.class);

    protected final SSLEngine sslEngine;

    protected @Nullable ChannelPromise handshakeResult;

    protected TlsHandler(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }

    @Override
    public void sparkChannelAfterConnected(NetChannel channel, ChannelInBoundHandlerChain chain) {
        handshake(channel)
                .onSuccess(ignored -> chain.sparkChannelAfterConnected(channel))
                .onFailure(throwable -> {
                    log.error("Failed to start TLS handshake: {}", throwable.getMessage(), throwable);
                    channel.close();
                });
    }

    public final ChannelPromise handshake(NetChannel channel) {
        ChannelPromise current = handshakeResult;
        if (current != null) {
            return current;
        }

        ChannelPromise created = ChannelPromise.newPromise(channel);
        handshakeResult = created;

        channel.eventLoop().submit(() -> {
            try {
                sslEngine.beginHandshake();
                log.debug("TLS handshake started");

                handleHandshake(channel, created);
            } catch (Throwable error) {
                created.fail(new NetSecureException("Failed to start TLS handshake", error));
            }
        });

        return created;
    }

    public final boolean isCompletedHandshake() {
        ChannelPromise result = handshakeResult;
        return result != null && result.isSuccess();
    }

    public final SSLSession session() {
        return sslEngine.getSession();
    }

    public final String peerHost() {
        return sslEngine.getPeerHost();
    }

    public final int peerPort() {
        return sslEngine.getPeerPort();
    }

    public final boolean isClosed() {
        return sslEngine.isInboundDone() && sslEngine.isOutboundDone();
    }

    public final SSLEngine sslEngine() {
        return sslEngine;
    }

    abstract void handleHandshake(NetChannel channel, ChannelPromise result);

    public abstract ChannelPromise close(NetChannel channel);
}
