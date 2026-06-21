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
package org.traffichunter.titan.fanout;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.HandlerChain;

/**
 * Starts the destination consumer after a message has been routed.
 *
 * @author yun
 */
final class DispatchFanoutHandler implements FanoutHandler {

    private final Consumer<Destination> fanout;

    DispatchFanoutHandler(Consumer<Destination> fanout) {
        this.fanout = fanout;
    }

    @Override
    public CompletableFuture<Void> handle(FanoutContext context, HandlerChain<FanoutContext> chain) {
        Message routed = context.getRoutedMessage();
        if (routed != null) {
            fanout.accept(routed.getDestination());
        }
        return chain.next(context);
    }
}
