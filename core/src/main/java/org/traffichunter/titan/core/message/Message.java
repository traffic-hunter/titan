/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.message;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.core.util.Destination;

/**
 * Message stored and routed by Titan's dispatcher queues.
 *
 * <p>The payload is kept as a heap byte array rather than a reference-counted transport buffer.
 * The constructor copies the supplied array, so queued messages do not retain codec or network
 * resources and do not require explicit release.</p>
 *
 * @author yungwang-o
 */
public final class Message {

    private final String uniqueId = IdGenerator.uuid();

    private final Destination destination;

    private final Instant createdAt;

    private Instant dispatchedAt;

    private final String producerId;

    private final long size;

    private final byte[] body;

    public Message(final Destination destination,
                   final Instant createdAt,
                   final String producerId,
                   final byte[] body
    ) {
        this.destination = Objects.requireNonNull(destination, "routingKey");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.producerId = Objects.requireNonNull(producerId, "producerId");
        this.body = Objects.requireNonNull(body, "body").clone();
        this.size = this.body.length;
    }

    public static MessageBuilder builder() {
        return new MessageBuilder();
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public Destination getDestination() {
        return destination;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public String getProducerId() {
        return producerId;
    }

    public long getSize() {
        return size;
    }

    public byte[] getBody() {
        return body;
    }

    public void setDispatchAt(final Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message message)) {
            return false;
        }
        return getSize() == message.getSize() && Objects.equals(
                getUniqueId(), message.getUniqueId()) && Objects.equals(
                getDestination(), message.getDestination()) && Objects.equals(getCreatedAt(),
                message.getCreatedAt()) && Objects.equals(getDispatchedAt(), message.getDispatchedAt())
                && Objects.equals(getProducerId(), message.getProducerId()) && Objects.deepEquals(
                getBody(), message.getBody());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getUniqueId(),
                getDestination(),
                getCreatedAt(),
                getDispatchedAt(),
                getProducerId(), getSize(), Arrays.hashCode(getBody())
        );
    }

    @Override
    public String toString() {
        return "{" +
                "uniqueId:'" + uniqueId + '\'' +
                ", routingKey:" + destination +
                ", createdAt:" + createdAt +
                ", dispatchedAt:" + dispatchedAt +
                ", producerId:'" + producerId + '\'' +
                ", size:" + size +
                ", body:" + Arrays.toString(body) +
                '}';
    }

    public static final class MessageBuilder {

        private Destination destination;
        private Instant createdAt;
        private String producerId;
        private byte[] body;

        private MessageBuilder() {
        }

        public MessageBuilder destination(Destination destination) {
            this.destination = destination;
            return this;
        }

        public MessageBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public MessageBuilder producerId(String producerId) {
            this.producerId = producerId;
            return this;
        }

        public MessageBuilder body(byte[] body) {
            this.body = body;
            return this;
        }

        public Message build() {
            return new Message(destination, createdAt, producerId, body);
        }
    }
}
