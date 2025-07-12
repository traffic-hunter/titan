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
import java.util.Objects;
import lombok.Getter;
import org.traffichunter.titan.core.util.IdGenerator;
import org.traffichunter.titan.servicediscovery.RoutingKey;

/**
 * @author yungwang-o
 */
@Getter
public abstract class AbstractMessage implements Comparable<AbstractMessage> {

    private final String uniqueId = IdGenerator.uuid();

    private final Priority priority;

    private final RoutingKey routingKey;

    private final Instant createdAt;

    private Instant dispatchedAt;

    private boolean isRecovery;

    private final String producerId;

    private final long size;

    protected AbstractMessage(final Priority priority,
                              final RoutingKey routingKey,
                              final Instant createdAt,
                              final boolean isRecovery,
                              final String producerId,
                              final long size) {

        this.priority = Objects.requireNonNull(priority, "priority");
        this.routingKey = Objects.requireNonNull(routingKey, "routingKey");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.isRecovery = isRecovery;
        this.producerId = Objects.requireNonNull(producerId, "producerId");
        this.size = size;
    }

    public void setRecover() {
        this.isRecovery = true;
    }

    public void setDispatchAt(final Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    @Override
    public int compareTo(final AbstractMessage o) {
        if(this.priority.getPriorityValue() == o.priority.getPriorityValue()) {
            return this.createdAt.compareTo(o.createdAt);
        }
        return this.priority.getPriorityValue() - o.priority.getPriorityValue();
    }
}
