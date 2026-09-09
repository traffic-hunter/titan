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
package org.traffichunter.titan.core.channel;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.traffichunter.titan.core.util.Clearable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base implementation for handler chains backed by a singly linked list.
 *
 * <p>Iteration skips the supplied sentinel head. The sentinel removes special cases when
 * inserting or removing the first handler; the cached tail makes appends constant-time.
 * Subclasses wrap handlers in nodes and define how execution starts and moves between nodes.</p>
 *
 * <p>Changes to the links are unsynchronized. Channel chains should be configured before use
 * or changed only by their owning event loop. Dispatch chains follow the
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
