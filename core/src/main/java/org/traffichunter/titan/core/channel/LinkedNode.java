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
package org.traffichunter.titan.core.channel;

import org.jspecify.annotations.Nullable;

/**
 * Node in a singly linked handler chain.
 *
 * <p>Each link uses the chain's concrete node type. The shared linked-list implementation does
 * not need access to handler-specific state. A {@code null} next node marks the end of the chain.</p>
 *
 * <p>Insertion and removal change links between adjacent nodes. Implementations need not be
 * thread-safe. Assemble a chain before sharing it across threads, or change it only on its
 * owning event loop.</p>
 *
 * @param <NODE> concrete node type
 *
 * @author yun
 */
public interface LinkedNode<NODE extends LinkedNode<NODE>> {

    /**
     * Returns the following node, or {@code null} when this node is the tail.
     */
    @Nullable NODE next();

    /**
     * Replaces the following node.
     *
     * @param next following node, or {@code null} to detach the current tail
     */
    void next(@Nullable NODE next);
}
