/**
 * Lightweight asynchronous primitives used by Titan event loops.
 *
 * <p>The package is intentionally smaller than {@link java.util.concurrent.CompletableFuture}.
 * Promises are tied to an {@code EventLoop}; task execution and listener notification are
 * routed back to that loop so transport code can preserve single-threaded channel ownership.</p>
 *
 * <p>Core types:</p>
 *
 * <ul>
 *     <li>{@link org.traffichunter.titan.core.concurrent.Promise}: runnable future plus manual completion.</li>
 *     <li>{@link org.traffichunter.titan.core.concurrent.ChannelPromise}: promise carrying a channel context.</li>
 *     <li>{@link org.traffichunter.titan.core.concurrent.ScheduledPromise}: promise scheduled by event-loop deadline.</li>
 * </ul>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.core.concurrent;

import org.jspecify.annotations.NullMarked;
