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
package org.traffichunter.titan.core.codec.stomp;

import org.traffichunter.titan.core.channel.Subscription;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.DestinationGroups;

/**
 * @author yun
 */
public class StompServerSubscription extends Subscription implements StompSubscription {

    private final String ackMode;
    private final StompClientChannel connection;

    /** Subscription in the default group. */
    public StompServerSubscription(
            Destination destination,
            String id,
            String ackMode,
            StompClientChannel connection
    ) {
        this(DestinationGroups.DEFAULT, destination, id, ackMode, connection);
    }

    public StompServerSubscription(
            String group,
            Destination destination,
            String id,
            String ackMode,
            StompClientChannel connection
    ) {
        super(group, destination, id);
        this.ackMode = ackMode;
        this.connection = connection;
    }

    public static StompServerSubscriptionBuilder builder() {
        return new StompServerSubscriptionBuilder();
    }

    public String getAckMode() {
        return ackMode;
    }

    public StompClientChannel getConnection() {
        return connection;
    }

    @Override
    public String id() {
        return getId();
    }

    @Override
    public Destination destination() {
        return getDestination();
    }

    public static final class StompServerSubscriptionBuilder {

        private @Nullable String group;
        private Destination destination;
        private String id;
        private String ackMode;
        private StompClientChannel connection;

        private StompServerSubscriptionBuilder() {
        }

        /** Null or blank means the default group. */
        public StompServerSubscriptionBuilder group(@Nullable String group) {
            this.group = group;
            return this;
        }

        public StompServerSubscriptionBuilder destination(Destination destination) {
            this.destination = destination;
            return this;
        }

        public StompServerSubscriptionBuilder id(String id) {
            this.id = id;
            return this;
        }

        public StompServerSubscriptionBuilder ackMode(String ackMode) {
            this.ackMode = ackMode;
            return this;
        }

        public StompServerSubscriptionBuilder connection(StompClientChannel connection) {
            this.connection = connection;
            return this;
        }

        public StompServerSubscription build() {
            return new StompServerSubscription(
                    DestinationGroups.normalize(group), destination, id, ackMode, connection);
        }
    }
}
