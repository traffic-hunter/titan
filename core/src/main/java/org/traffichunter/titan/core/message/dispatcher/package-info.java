/**
 * Message dispatching primitives.
 *
 * <p>A dispatcher maps a {@link org.traffichunter.titan.core.util.Destination} to a
 * {@link org.traffichunter.titan.core.message.dispatcher.DispatcherQueue}. Producers enqueue
 * messages into the destination queue, and consumers drain those queues through blocking or
 * timed dispatch calls.</p>
 *
 * <p>The default implementation is trie-based so destination lookup can share path prefixes.
 * Queue implementations are intentionally small: they own only ordering, pause/resume, and
 * back-pressure inspection for one destination.</p>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.core.message.dispatcher;

import org.jspecify.annotations.NullMarked;