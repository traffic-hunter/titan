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
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Handler;

/**
 * @author yun
 */
public class StompClientSubscription extends Subscription implements StompSubscription {

    private final Handler<StompFrame> handler;

    public StompClientSubscription(
            String destination,
            String id,
            Handler<StompFrame> handler
    ) {
        super(Destination.create(destination), id);
        this.handler = handler;
    }

    public static StompClientSubscriptionBuilder builder() {
        return new StompClientSubscriptionBuilder();
    }

    public Handler<StompFrame> getHandler() {
        return handler;
    }

    @Override
    public String id() {
        return getId();
    }

    @Override
    public Destination destination() {
        return getDestination();
    }

    public static final class StompClientSubscriptionBuilder {

        private String destination;
        private String id;
        private Handler<StompFrame> handler;

        private StompClientSubscriptionBuilder() {
        }

        public StompClientSubscriptionBuilder destination(String destination) {
            this.destination = destination;
            return this;
        }

        public StompClientSubscriptionBuilder id(String id) {
            this.id = id;
            return this;
        }

        public StompClientSubscriptionBuilder handler(Handler<StompFrame> handler) {
            this.handler = handler;
            return this;
        }

        public StompClientSubscription build() {
            return new StompClientSubscription(destination, id, handler);
        }
    }
}
