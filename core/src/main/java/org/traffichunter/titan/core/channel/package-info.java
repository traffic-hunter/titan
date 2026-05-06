/**
 * Core channel and event-loop abstractions.
 *
 * <p>The package is organized around a small transport pipeline:</p>
 *
 * <ul>
 *     <li>{@link org.traffichunter.titan.core.channel.Channel} represents a selectable I/O endpoint.</li>
 *     <li>{@link org.traffichunter.titan.core.channel.IOEventLoop} owns selector operations and channel tasks.</li>
 *     <li>{@link org.traffichunter.titan.core.channel.EventLoopGroups} separates accept work from read/write work.</li>
 *     <li>{@link org.traffichunter.titan.core.channel.ChannelHandlerChain} dispatches channel read/write lifecycle events.</li>
 * </ul>
 *
 * <p>Server channels are registered on the primary event loop. Accepted {@code NetChannel}
 * instances move to secondary event loops before read/write events are registered.</p>
 *
 * @author yun
 */
@NullMarked
package org.traffichunter.titan.core.channel;

import org.jspecify.annotations.NullMarked;
