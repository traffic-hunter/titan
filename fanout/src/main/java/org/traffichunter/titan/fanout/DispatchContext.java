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

import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.core.message.Message;

/**
 * Mutable state shared while one dispatch operation moves through a handler chain.
 *
 * <p>The context starts with the original producer {@link Message}. The route
 * handler stores the routed message after enqueueing it, and later handlers use
 * that routed value to decide whether the operation should continue.</p>
 *
 * @author yun
 */
public class DispatchContext {

    private final Message message;
    private @Nullable Message routedMessage;

    public DispatchContext(Message message) {
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }

    public @Nullable Message getRoutedMessage() {
        return routedMessage;
    }

    public void setRoutedMessage(@Nullable Message routedMessage) {
        this.routedMessage = routedMessage;
    }
}
