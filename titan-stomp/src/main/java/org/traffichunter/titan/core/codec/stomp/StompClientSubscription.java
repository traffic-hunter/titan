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
