/**
 * Fanout module entry points.
 *
 * <p>The fanout module connects inbound producer traffic to all consumers that
 * have subscribed to the same {@code Destination}. Its runtime shape is:</p>
 *
 * <pre>{@code
 * Producer SEND frame
 *        |
 *        v
 * StompSendToFanoutHandler
 *        |
 *        v
 * FanoutGateway.publish(message)
 *        |
 *        v
 * DispatcherQueue per Destination
 *        |
 *        v
 * FanoutGateway consumer task
 *        |
 *        v
 * FanoutExporter (STOMP, TCP, ...)
 *        |
 *        v
 * subscribed clients
 * }</pre>
 *
 * <p>{@link org.traffichunter.titan.fanout.FanoutGateway} owns the asynchronous
 * queue consumers, while exporter implementations own protocol-specific delivery.
 * This keeps message routing independent from the protocol used to write the
 * payload to connected clients.</p>
 */
@NullMarked
package org.traffichunter.titan.fanout;

import org.jspecify.annotations.NullMarked;
