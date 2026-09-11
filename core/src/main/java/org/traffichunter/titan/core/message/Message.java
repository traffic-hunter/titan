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
package org.traffichunter.titan.core.message;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;
import org.traffichunter.titan.core.util.IdGenerator;

/**
 * Message stored and routed by Titan's dispatcher queues.
 *
 * <p>The payload is kept as a heap byte array rather than a reference-counted transport buffer.
 * The constructor copies the supplied array, so queued messages do not retain codec or network
 * resources and do not require explicit release.</p>
 *
 * <p>Every message belongs to a destination group. Messages built without one belong to
 * {@link DestinationGroups#DEFAULT}.</p>
 *
 * @author yungwang-o
 */
public final class Message {

    private final String uniqueId = IdGenerator.uuid();

    private final String group;

    private final Destination destination;

    private final Instant createdAt;

    private Instant dispatchedAt;

    private final String producerId;

    private final long size;

    private final byte[] body;

    /** Message in the default group. */
    public Message(final Destination destination,
                   final Instant createdAt,
                   final String producerId,
                   final byte[] body
    ) {
        this(DestinationGroups.DEFAULT, destination, createdAt, producerId, body);
    }

    public Message(final String group,
                   final Destination destination,
                   final Instant createdAt,
                   final String producerId,
                   final byte[] body
    ) {
        this.group = Objects.requireNonNull(group, "group");
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

    public String getGroup() {
        return group;
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
                getGroup(), message.getGroup()) && Objects.equals(
                getDestination(), message.getDestination()) && Objects.equals(getCreatedAt(),
                message.getCreatedAt()) && Objects.equals(getDispatchedAt(), message.getDispatchedAt())
                && Objects.equals(getProducerId(), message.getProducerId()) && Objects.deepEquals(
                getBody(), message.getBody());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getUniqueId(),
                getGroup(),
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
                ", group:'" + group + '\'' +
                ", routingKey:" + destination +
                ", createdAt:" + createdAt +
                ", dispatchedAt:" + dispatchedAt +
                ", producerId:'" + producerId + '\'' +
                ", size:" + size +
                ", body:" + Arrays.toString(body) +
                '}';
    }

    public static final class MessageBuilder {

        private @Nullable String group;
        private Destination destination;
        private Instant createdAt;
        private String producerId;
        private byte[] body;

        private MessageBuilder() {
        }

        /** Null or blank means the default group. */
        public MessageBuilder group(@Nullable String group) {
            this.group = group;
            return this;
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
            return new Message(DestinationGroups.normalize(group), destination, createdAt, producerId, body);
        }
    }
}
