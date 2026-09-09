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
