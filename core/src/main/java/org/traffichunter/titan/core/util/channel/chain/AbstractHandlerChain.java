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
package org.traffichunter.titan.core.util.channel.chain;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.concurrent.CompletableFuture;

/**
 * @author yun
 */
public abstract class AbstractHandlerChain<NODE extends LinkedHandlerChainNode<CTX>, CTX>
        implements HandlerChain<CTX>, AutoCloseable {

    private final NODE head;
    private NODE tail;

    protected AbstractHandlerChain(NODE head) {
        this.head = this.tail = head;
    }

    protected final NODE head() {
        return head;
    }

    protected final NODE tail() {
        return tail;
    }

    @CanIgnoreReturnValue
    protected final AbstractHandlerChain<NODE, CTX> add(NODE node) {
        return addLast(node);
    }

    @CanIgnoreReturnValue
    public final AbstractHandlerChain<NODE, CTX> addFirst(NODE node) {
        node.next(head.next());
        head.next(node);
        if (tail == head) {
            tail = node;
        }
        return this;
    }

    @CanIgnoreReturnValue
    public final AbstractHandlerChain<NODE, CTX> addLast(NODE node) {
        tail.next(node);
        tail = node;
        return this;
    }

    @Override
    public CompletableFuture<Void> sparkChainHandler(CTX context) {
        return head.sparkChainHandler(context);
    }

    @Override
    public CompletableFuture<Void> next(CTX context) {
        return sparkChainHandler(context);
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        LinkedHandlerChainNode<CTX> node = head.next();
        while (node != null) {
            if (node instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            node = node.next();
        }

        if (failure != null) {
            throw failure;
        }
    }
}
