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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traffichunter.titan.core.channel.ChannelOutBoundHandlerChain;
import org.traffichunter.titan.core.channel.NetChannel;
import org.traffichunter.titan.core.concurrent.ChannelPromise;
import org.traffichunter.titan.core.concurrent.Promise;
import org.traffichunter.titan.core.util.buffer.Buffer;
import org.traffichunter.titan.core.util.buffer.Buffers;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

/**
 * @author yun
 */
class JdkTlsHandler extends TlsHandler {

    private static final Logger log = LoggerFactory.getLogger(JdkTlsHandler.class);

    private final TlsTaskExecutor taskExecutor;
    private @Nullable ChannelPromise closeResult;
    private boolean delegatedTaskRunning;

    JdkTlsHandler(SSLEngine sslEngine) {
        this(sslEngine, TlsTaskExecutor.immediate());
    }

    JdkTlsHandler(SSLEngine sslEngine, TlsTaskExecutor taskExecutor) {
        super(sslEngine);
        this.taskExecutor = taskExecutor;
    }

    @Override
    void handleHandshake(NetChannel channel, ChannelPromise handshakeResult) {
        if (handshakeResult.isDone()) {
            return;
        }

        try {
            while (!handshakeResult.isDone()) {
                switch (sslEngine.getHandshakeStatus()) {
                    case NEED_TASK -> {
                        if (!executeDelegatedTasks(channel, handshakeResult)) {
                            return;
                        }
                    }
                    case NEED_UNWRAP -> {
                        return;
                    }
                    case NEED_UNWRAP_AGAIN -> unwrapAgain(channel);
                    case NEED_WRAP -> writeHandshake(channel);
                    case FINISHED, NOT_HANDSHAKING -> {
                        handshakeResult.success();
                        return;
                    }
                }
            }
        } catch (Throwable error) {
            handshakeResult.fail(new NetSecureException("Failed to handle TLS handshake", error));
            channel.close();
        }
    }

    @Override
    public void sparkChannelWrite(NetChannel channel, Buffer plainText, ChannelOutBoundHandlerChain chain) {
        Buffer encrypted;
        try {
            if (!isCompletedHandshake() || sslEngine.isOutboundDone()) {
                throw new NetSecureException("TLS channel is not in a valid state for writing");
            }

            while (plainText.isReadable()) {
                encrypted = wrap(plainText);
                chain.sparkChannelWrite(channel, encrypted);
            }
        } catch (Throwable error) {
            chain.sparkExceptionCaught(error);
            channel.close();
        } finally {
            plainText.release();
        }
    }

    @Override
    public void sparkExceptionCaught(Throwable error, ChannelOutBoundHandlerChain chain) {
        log.error("Failed to write data to TLS channel", error);
        chain.sparkExceptionCaught(error);
    }

    @Override
    protected @Nullable Buffer decode(NetChannel channel, Buffer encrypted) {
        if (delegatedTaskRunning) {
            return null;
        }

        try {
            Buffer plainText = unwrap(channel, encrypted);
            ChannelPromise result = handshakeResult;
            if (result != null && !result.isDone()) {
                handleHandshake(channel, result);
            }

            if (plainText != null && !plainText.isReadable()) {
                plainText.release();
                return null;
            }
            return plainText;
        } catch (Throwable error) {
            ChannelPromise result = handshakeResult;
            if (result != null && !result.isDone()) {
                result.fail(error);
            }
            channel.close();
            return null;
        }
    }

    private boolean executeDelegatedTasks(NetChannel channel, ChannelPromise result) {
        if (delegatedTaskRunning) {
            return false;
        }

        if (taskExecutor == TlsTaskExecutor.immediate()) {
            runDelegatedTasks();
            return true;
        }

        delegatedTaskRunning = true;
        taskExecutor.execute(() -> {
            try {
                runDelegatedTasks();
                resumeHandshake(channel, result, null);
            } catch (Throwable error) {
                resumeHandshake(channel, result, error);
            }
        });
        return false;
    }

    private void runDelegatedTasks() {
        Runnable task;
        while ((task = sslEngine.getDelegatedTask()) != null) {
            task.run();
        }
    }

    private void resumeHandshake(
            NetChannel channel,
            ChannelPromise result,
            @Nullable Throwable taskFailure
    ) {
        channel.eventLoop().register(() -> {
            delegatedTaskRunning = false;

            if (taskFailure != null) {
                result.fail(new NetSecureException("Failed to execute delegated TLS task", taskFailure));
                channel.close();
                return;
            }
            if (result.isDone() || channel.isClosed()) {
                return;
            }

            handleHandshake(channel, result);
            if (!result.isFailed() && !channel.isClosed()) {
                resumeDecode(channel);
            }
        });
    }

    @Override
    public ChannelPromise close(NetChannel channel) {
        if (closeResult != null) {
            return closeResult;
        }

        ChannelPromise channelPromise = ChannelPromise.newPromise(channel);
        closeResult = channelPromise;

        Promise<Void> result = channel.eventLoop().submit(() -> {
            if (!sslEngine.isOutboundDone()) {
                sslEngine.closeOutbound();
            }

            Buffer closeNotify;
            try {
                closeNotify = wrapCloseNotify();
            } catch (SSLException e) {
                throw new NetSecureException("Failed to create TLS close_notify", e);
            }

            if (!closeNotify.isReadable()) {
                closeNotify.release();
                return;
            }

            write(channel, closeNotify);
        });
        result.onSuccess(ignored -> channelPromise.success());
        result.onFailure(error -> {
            channelPromise.fail(error);
            channel.close();
        });

        return channelPromise;
    }

    private Buffer wrap(Buffer plainBuffer) throws SSLException {
        int packetSize = sslEngine.getSession().getPacketBufferSize();
        Buffer encrypted = Buffer.direct().alloc(packetSize);
        boolean success = false;

        try {
            while (true) {
                ByteBuffer src = Buffers.readableByteBuffer(plainBuffer);
                ByteBuffer dest = Buffers.writableByteBuffer(encrypted);

                SSLEngineResult sslResult = sslEngine.wrap(src, dest);

                plainBuffer.skipBytes(sslResult.bytesConsumed());
                Buffers.updateWriterIndex(encrypted, sslResult.bytesProduced());

                switch (sslResult.getStatus()) {
                    case OK -> {
                        if (sslResult.bytesProduced() == 0 && sslResult.bytesConsumed() == 0) {
                            throw new NetSecureException("TLS wrap made no progress: " + sslResult.getHandshakeStatus());
                        }

                        success = true;
                        return encrypted;
                    }
                    case BUFFER_OVERFLOW -> {
                        int newSize = sslEngine.getSession().getPacketBufferSize();
                        encrypted.expand(newSize);
                    }
                    case BUFFER_UNDERFLOW ->
                            throw new NetSecureException("Unexpected underflow while wrapping TLS data");
                    case CLOSED -> throw new NetSecureException("TLS outbound is closed");
                    default -> throw new NetSecureException("Unknown TLS status: " + sslResult.getStatus());
                }
            }
        } finally {
            if (!success) {
                encrypted.release();
            }
        }
    }

    private @Nullable Buffer unwrap(NetChannel channel, Buffer encrypted) throws SSLException {
        int appBufferSize = sslEngine.getSession().getApplicationBufferSize();
        Buffer plainText = Buffer.direct().alloc(appBufferSize);
        boolean success = false;

        try {
            while (true) {
                ByteBuffer src = Buffers.readableByteBuffer(encrypted);
                ByteBuffer dest = Buffers.writableByteBuffer(plainText);

                SSLEngineResult sslResult = sslEngine.unwrap(src, dest);

                encrypted.skipBytes(sslResult.bytesConsumed());
                Buffers.updateWriterIndex(plainText, sslResult.bytesProduced());

                switch (sslResult.getStatus()) {
                    case OK -> {
                        success = true;
                        return plainText;
                    }
                    case BUFFER_OVERFLOW -> {
                        int newSize = sslEngine.getSession().getApplicationBufferSize();
                        plainText.expand(newSize);
                    }
                    case BUFFER_UNDERFLOW -> {
                        return null;
                    }
                    case CLOSED -> {
                        success = true;
                        close(channel);
                        return plainText;
                    }
                    default -> throw new NetSecureException("Unknown TLS status: " + sslResult.getStatus());
                }
            }
        } finally {
            if (!success) {
                plainText.release();
            }
        }
    }

    private void writeHandshake(NetChannel channel) throws SSLException {
        Buffer empty = Buffer.heap().empty();
        Buffer encrypted = null;
        try {
            encrypted = wrap(empty);
            if (!encrypted.isReadable()) {
                return;
            }

            // The handshake packet is already encrypted and must bypass this outbound handler.
            Buffer handshakeRecord = encrypted;
            encrypted = null;
            write(channel, handshakeRecord);
        } finally {
            empty.release();
            if (encrypted != null) {
                encrypted.release();
            }
        }
    }

    private void unwrapAgain(NetChannel channel) throws SSLException {
        Buffer empty = Buffer.heap().empty();
        Buffer plainText = null;
        try {
            plainText = unwrap(channel, empty);
            if (plainText != null && plainText.isReadable()) {
                throw new NetSecureException("Unexpected application data while advancing TLS handshake");
            }
        } finally {
            empty.release();
            if (plainText != null) {
                plainText.release();
            }
        }
    }

    private void write(NetChannel channel, Buffer buffer) {
        boolean accepted = false;
        try {
            channel.internal().write(buffer);
            accepted = true;
            channel.internal().flush();
        } finally {
            if (!accepted) {
                buffer.release();
            }
        }
    }

    private Buffer wrapCloseNotify() throws SSLException {
        Buffer source = Buffer.heap().empty();
        Buffer encrypted = Buffer.direct().alloc(sslEngine.getSession().getPacketBufferSize());
        boolean success = false;

        try {

            while (!sslEngine.isOutboundDone()) {
                SSLEngineResult sslResult = sslEngine.wrap(
                        Buffers.readableByteBuffer(source),
                        Buffers.writableByteBuffer(encrypted)
                );

                Buffers.updateWriterIndex(encrypted, sslResult.bytesProduced());

                switch (sslResult.getStatus()) {
                    case OK, CLOSED -> {
                        // Continue until outbound is completely closed.
                    }
                    case BUFFER_OVERFLOW ->
                            encrypted.expand(sslEngine.getSession().getPacketBufferSize());
                    case BUFFER_UNDERFLOW ->
                            throw new NetSecureException("Unexpected underflow while creating TLS close_notify");
                }

                if (sslResult.bytesConsumed() == 0 && sslResult.bytesProduced() == 0 && !sslEngine.isOutboundDone()) {
                    throw new NetSecureException("TLS close_notify wrap made no progress");
                }
            }

            success = true;
            return encrypted;
        } finally {
            source.release();
            if (!success) {
                encrypted.release();
            }
        }
    }
}
