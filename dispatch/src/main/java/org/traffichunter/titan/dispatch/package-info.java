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
 * DispatchGateway.sparkDispatch(message)
 *        |
 *        v
 * DispatcherQueue per Destination
 *        |
 *        v
 * FanoutDispatchChainHandler consumer task
 *        |
 *        v
 * DispatchExporter (STOMP, TCP, ...)
 *        |
 *        v
 * subscribed clients
 * }</pre>
 *
 * <p>{@link org.traffichunter.titan.dispatch.DispatchGateway} starts the dispatch chain,
 * {@link org.traffichunter.titan.dispatch.Dispatcher} resolves destination queues, and exporter
 * implementations own protocol-specific delivery. This keeps message routing independent from
 * the protocol used to write the payload to connected clients.</p>
 */
@NullMarked
package org.traffichunter.titan.dispatch;

import org.jspecify.annotations.NullMarked;
