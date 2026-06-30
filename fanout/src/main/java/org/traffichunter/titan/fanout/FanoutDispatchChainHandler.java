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
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.Message;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.channel.chain.HandlerChain;

/**
 * Starts the destination consumer after a message has been routed.
 *
 * <p>The consumer task is started but not awaited, because publish
 * acknowledgements should not wait for the long-lived consumer loop to finish.</p>
 *
 * @author yun
 */
final class FanoutDispatchChainHandler implements DispatchChainHandler {

    private final Function<Destination, CompletableFuture<@Nullable Void>> fanout;

    FanoutDispatchChainHandler(Function<Destination, CompletableFuture<@Nullable Void>> fanout) {
        this.fanout = fanout;
    }

    @Override
    public CompletableFuture<Void> handle(DispatchContext context, HandlerChain<DispatchContext> chain) {
        Message routed = context.getRoutedMessage();
        if (routed == null) {
            return chain.sparkChainHandler(context);
        }
        fanout.apply(routed.getDestination());
        return chain.sparkChainHandler(context);
    }
}
