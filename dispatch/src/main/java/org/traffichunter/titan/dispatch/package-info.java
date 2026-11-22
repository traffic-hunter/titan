/**
 * Dispatch module entry points.
 *
 * <p>The dispatch module connects inbound producer traffic to all consumers that
 * have subscribed to the same {@code Destination}. Its runtime shape is:</p>
 *
 * <pre>{@code
 * Producer SEND frame
 *        |
 *        v
 * StompSendToFanoutHandler
 *        |
 *        v
 * Gateway.sparkDispatch(message)
 *        |
 *        v
 * DispatcherQueue per Destination
 *        |
 *        v
 * FanoutDispatcher consumer task
 *        |
 *        v
 * DispatchExporter (STOMP, TCP, ...)
 *        |
 *        v
 * subscribed clients
 * }</pre>
 *
 * <p>{@link org.traffichunter.titan.dispatch.Gateway} routes a message into its queue,
 * {@link org.traffichunter.titan.dispatch.PathFinder} resolves destination queues,
 * {@link org.traffichunter.titan.dispatch.Dispatcher} delivers from the queues, and exporter
 * implementations own protocol-specific delivery. Gateway and dispatcher are built separately on
 * the same path finder. This keeps message routing independent from the protocol used to write
 * the payload to connected clients.</p>
 */
@NullMarked
package org.traffichunter.titan.dispatch;

import org.jspecify.annotations.NullMarked;
