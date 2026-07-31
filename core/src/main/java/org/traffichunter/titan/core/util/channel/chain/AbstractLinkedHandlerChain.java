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
import org.traffichunter.titan.core.util.Clearable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base implementation for handler chains backed by a singly linked list.
 *
 * <p>The supplied head is a sentinel node and is never exposed by iteration. Keeping a sentinel
 * makes first insertion and removal uniform, while the cached tail keeps append operations
 * constant-time. Subclasses remain responsible for wrapping domain handlers in nodes and for
 * defining how execution enters and advances through those nodes.</p>
 *
 * <p>Structural operations are intentionally unsynchronized. Channel chains are expected to be
 * configured before use or mutated only by their owning event loop. Dispatch chains follow the
 * same rule unless an external synchronization policy is provided.</p>
 *
 * <p>{@link #clear()} detaches all user nodes and restores the sentinel as the tail. It only
 * resets the linked structure; it does not close handlers or manage resources owned by them.</p>
 * @param <NODE> concrete node type
 *
 * @author yun
 */
public abstract class AbstractLinkedHandlerChain<NODE extends LinkedNode<NODE>> implements Clearable {

    private final NODE head;
    private NODE tail;

    protected AbstractLinkedHandlerChain(NODE head) {
        this.head = this.tail = head;
    }

    /**
     * Returns the sentinel node used to enter the concrete chain.
     */
    protected final NODE head() {
        return head;
    }

    /**
     * Returns the last structural node, or the sentinel when the chain is empty.
     */
    protected final NODE tail() {
        return tail;
    }

    @CanIgnoreReturnValue
    protected final AbstractLinkedHandlerChain<NODE> add(NODE node) {
        return addLast(node);
    }

    /**
     * Inserts a node immediately after the sentinel.
     */
    @CanIgnoreReturnValue
    public final AbstractLinkedHandlerChain<NODE> addFirst(NODE node) {
        node.next(head.next());
        head.next(node);
        if (tail == head) {
            tail = node;
        }
        return this;
    }

    /**
     * Appends a node and updates the cached tail.
     */
    @CanIgnoreReturnValue
    public final AbstractLinkedHandlerChain<NODE> addLast(NODE node) {
        tail.next(node);
        tail = node;
        return this;
    }

    /**
     * Removes the first matching user node and repairs the tail when necessary.
     *
     * @return {@code true} when a matching node was detached
     */
    protected final boolean removeFirst(Predicate<? super NODE> predicate) {
        NODE previous = head;
        NODE current = head.next();
        while (current != null) {
            if (predicate.test(current)) {
                previous.next(current.next());
                if (tail == current) {
                    tail = previous;
                }
                current.next(null);
                return true;
            }
            previous = current;
            current = current.next();
        }
        return false;
    }

    /**
     * Visits user nodes in insertion order. The sentinel is excluded.
     */
    public final void forEach(Consumer<? super NODE> consumer) {
        NODE node = head.next();
        while (node != null) {
            consumer.accept(node);
            node = node.next();
        }
    }

    /**
     * Detaches every user node and restores this chain to its empty state.
     *
     * <p>Links are removed individually so externally retained nodes no longer keep the remainder
     * of the old chain reachable. The chain can accept new nodes immediately after this method
     * returns.</p>
     */
    @Override
    public final void clear() {
        NODE node = head.next();
        while (node != null) {
            NODE next = node.next();
            node.next(null);
            node = next;
        }
        head.next(null);
        tail = head;
    }

}
