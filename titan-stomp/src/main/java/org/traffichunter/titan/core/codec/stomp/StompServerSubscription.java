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
package org.traffichunter.titan.core.codec.stomp;

import org.traffichunter.titan.core.channel.Subscription;
import org.traffichunter.titan.core.channel.stomp.StompClientChannel;
import org.traffichunter.titan.core.util.Destination;

/**
 * @author yun
 */
public class StompServerSubscription extends Subscription implements StompSubscription {

    private final String ackMode;
    private final StompClientChannel connection;

    public StompServerSubscription(
            Destination destination,
            String id,
            String ackMode,
            StompClientChannel connection
    ) {
        super(destination, id);
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

        private Destination destination;
        private String id;
        private String ackMode;
        private StompClientChannel connection;

        private StompServerSubscriptionBuilder() {
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
            return new StompServerSubscription(destination, id, ackMode, connection);
        }
    }
}
